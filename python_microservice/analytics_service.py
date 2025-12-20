from flask import Flask, request, jsonify
from flask_cors import CORS
import zipfile
import tempfile
import os
import sys
import re
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
import glob
import traceback
import pandas as pd
import emoji as emoji_lib
import uuid
import time
from threading import Lock
from cachetools import TTLCache
from collections import Counter
import tiktoken

# Add stats_computation directory to Python path to import parser
stats_computation_path = os.path.join(os.path.dirname(__file__), '..', 'stats_computation')
if stats_computation_path not in sys.path:
    sys.path.insert(0, stats_computation_path)

# Now import the parser and its dependencies
try:
    from parser import WhatsAppChatParser
    print("✓ Successfully imported WhatsAppChatParser")
except ImportError as e:
    print(f"✗ Failed to import parser: {e}")
    traceback.print_exc()

load_dotenv()

app = Flask(__name__)
CORS(app, resources={r"/*": {"origins": "*"}})
app.json.ensure_ascii = False

# ============================================================
# EPHEMERAL RAG CACHE (TTL = 10 minutes)
# ============================================================
RAG_CACHE_TTL = 600  # 10 minutes in seconds
rag_cache = TTLCache(maxsize=100, ttl=RAG_CACHE_TTL)
rag_cache_lock = Lock()

# Token limit for context (128k tokens, leaving room for prompt and response)
MAX_CONTEXT_TOKENS = 120000  # Leave 8k for prompt template and response

# Initialize tiktoken encoder for gpt-4o-mini
try:
    TOKEN_ENCODER = tiktoken.encoding_for_model("gpt-4o-mini")
except Exception:
    TOKEN_ENCODER = tiktoken.get_encoding("cl100k_base")


def count_tokens(text):
    """Count tokens in text using tiktoken."""
    return len(TOKEN_ENCODER.encode(text))


def create_rag_session(df, session_id=None):
    """Create a RAG session from chat DataFrame with TTL expiration.
    
    Instead of using embeddings, we store the most recent messages
    that fit within the 128k token context window.
    """
    
    if session_id is None:
        session_id = str(uuid.uuid4())
    
    # Convert messages to formatted strings
    message_col = 'message' if 'message' in df.columns else 'content'
    
    # Sort by datetime to ensure chronological order (most recent last)
    if 'datetime' in df.columns:
        df = df.sort_values('datetime')
    
    # Format all messages
    formatted_messages = []
    for _, row in df.iterrows():
        content = f"[{row.get('datetime', 'Unknown')}] {row['sender']}: {row.get(message_col, '')}"
        formatted_messages.append(content)
    
    # Take the most recent messages that fit within token limit
    # Start from the end (most recent) and work backwards
    selected_messages = []
    total_tokens = 0
    
    for msg in reversed(formatted_messages):
        msg_tokens = count_tokens(msg + "\n")
        if total_tokens + msg_tokens <= MAX_CONTEXT_TOKENS:
            selected_messages.insert(0, msg)  # Insert at beginning to maintain order
            total_tokens += msg_tokens
        else:
            break
    
    # Join selected messages into context string
    context_text = "\n".join(selected_messages)
    
    # Store in cache with TTL
    with rag_cache_lock:
        rag_cache[session_id] = {
            "context": context_text,
            "context_tokens": total_tokens,
            "total_messages": len(df),
            "included_messages": len(selected_messages),
            "created_at": time.time(),
            "message_count": len(df),
            "participants": list(df['sender'].unique()),
        }
    
    app.logger.info(f"Created RAG session {session_id} with {len(selected_messages)}/{len(df)} messages ({total_tokens} tokens, expires in {RAG_CACHE_TTL}s)")
    return session_id


def get_rag_session(session_id):
    """Get RAG session if it exists and hasn't expired."""
    with rag_cache_lock:
        return rag_cache.get(session_id)


# ============================================================
# NEW ENDPOINTS FOR EPHEMERAL RAG
# ============================================================

@app.route("/rag/upload", methods=["POST"])
def rag_upload():
    """Upload a chat for ephemeral RAG. Returns session_id valid for 10 minutes."""
    file = request.files.get("file")
    if file is None or not file.filename.endswith(".zip"):
        return jsonify({"error": "Invalid file. Please upload a ZIP file."}), 400

    try:
        with tempfile.TemporaryDirectory() as tmpdir:
            # Save and extract ZIP
            zip_path = os.path.join(tmpdir, file.filename)
            file.save(zip_path)
            
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(tmpdir)

            # Find chat file
            chat_files = glob.glob(os.path.join(tmpdir, "**", "*.txt"), recursive=True)
            if not chat_files:
                return jsonify({"error": "No chat file found in ZIP"}), 400

            # Parse chat
            parser = WhatsAppChatParser()
            df = parser.parse_to_csv(chat_files[0])
            
            if df.empty:
                return jsonify({"error": "No messages found in chat"}), 400

            # Create RAG session
            session_id = create_rag_session(df)
            group_title = parser.group_name if parser.group_name else "WhatsApp Chat"

            #convert python dictionary to json
            return jsonify({
                "session_id": session_id,
                "title": group_title,
                "message_count": len(df),
                "participants": list(df['sender'].unique()),
                "expires_in_seconds": RAG_CACHE_TTL,
                "message": f"RAG session created. Valid for {RAG_CACHE_TTL // 60} minutes."
            })

    except Exception as e:
        app.logger.error(f"RAG upload error: {e}")
        app.logger.error(traceback.format_exc())
        return jsonify({"error": str(e)}), 500


@app.route("/rag/ask", methods=["POST"])
def rag_ask():
    """Ask a question about an uploaded chat using the most recent messages."""
    data = request.get_json()
    session_id = data.get("session_id")
    question = data.get("question")

    if not session_id or not question:
        return jsonify({"error": "session_id and question are required"}), 400

    # Get RAG session
    session = get_rag_session(session_id)
    if session is None:
        return jsonify({
            "error": "Session expired or not found. Please upload the chat again.",
            "expired": True
        }), 404

    try:
        # Get the pre-computed context (most recent messages up to 128k tokens)
        context = session["context"]
        included_messages = session["included_messages"]
        total_messages = session["total_messages"]
        
        app.logger.info(f"RAG query: using {included_messages}/{total_messages} most recent messages ({session['context_tokens']} tokens)")
        
        prompt = f"""
        You are an expert conversation analyst analyzing a WhatsApp chat.

        Your task is to answer the user's question using ONLY the chat messages provided.
        Do NOT use outside knowledge. Do NOT guess.

        CHAT MESSAGES
        (Most recent {included_messages} of {total_messages} total messages):
        --------------------
        {context}
        --------------------

        USER QUESTION:
        {question}

        ANALYSIS RULES:
        1. Understand the intent of the question before answering.
        - "Who talks more?" → count messages / message length.
        - "Who was more interested?" → look at initiation, response speed, message length, follow-ups.
        - "Who starts arguments?" → look for disagreements, corrections, debate-starting phrases.
        - Personality questions → infer patterns ONLY if there is repeated behavioral evidence.

        2. Search the chat for:
        - Keywords, phrases, emojis, timing patterns, message frequency
        - Who initiates topics, who responds, and how others react

        3. Use evidence:
        - Quote short, relevant message snippets when possible
        - Mention concrete signals (counts, ratios, timing, repetition)
        - Avoid long quotes — short fragments are enough

        4. Be honest:
        - If there is not enough evidence, say so clearly.
        - If the signal is weak or mixed, say that.

        RESPONSE STYLE:
        - Confident but neutral (no moral judgment)
        - Insightful and human-sounding (like a smart friend analyzing chats)
        - Short and punchy (2–4 sentences)
        - Natural language, NOT bullet points
        - Answer in the SAME language as the question (not the chat)

        OUTPUT FORMAT:
        - Start with a direct answer to the question
        - Follow with 1–2 concrete observations
        - Include quotes or measurable signals when relevant

        FINAL ANSWER:
        """


        # Generate answer with LLM
        model = ChatOpenAI(
            model="gpt-4o-mini",
            api_key=os.getenv("OPENAI_API_KEY"),
            temperature=0.7  # Higher = more creative and spontaneous
        )
        
        response = model.invoke(prompt)
        
        # Calculate remaining TTL
        time_elapsed = time.time() - session["created_at"]
        time_remaining = max(0, RAG_CACHE_TTL - time_elapsed)

        return jsonify({
            "answer": response.content,
            "messages_used": included_messages,
            "total_messages": total_messages,
            "session_id": session_id,
            "expires_in_seconds": int(time_remaining),
        })

    except Exception as e:
        app.logger.error(f"RAG ask error: {e}")
        app.logger.error(traceback.format_exc())
        return jsonify({"error": str(e)}), 500


@app.route("/rag/status/<session_id>", methods=["GET"])
def rag_status(session_id):
    """Check if a RAG session is still valid."""
    session = get_rag_session(session_id)
    
    if session is None:
        return jsonify({
            "valid": False,
            "message": "Session expired or not found"
        })
    
    time_elapsed = time.time() - session["created_at"]
    time_remaining = max(0, RAG_CACHE_TTL - time_elapsed)
    
    return jsonify({
        "valid": True,
        "session_id": session_id,
        "message_count": session["message_count"],
        "included_messages": session["included_messages"],
        "context_tokens": session["context_tokens"],
        "participants": session["participants"],
        "expires_in_seconds": int(time_remaining),
    })


def compute_chart_data(df):
    """Compute all aggregated data for frontend charts."""
    
    # Ensure datetime column exists
    if 'datetime' not in df.columns and 'timestamp' in df.columns:
        df['datetime'] = pd.to_datetime(df['timestamp'], errors='coerce')
    elif 'datetime' in df.columns:
        df['datetime'] = pd.to_datetime(df['datetime'], errors='coerce')
    
    # Drop rows with invalid datetime
    df = df.dropna(subset=['datetime'])
    
    # ========== FILTER OUT ISOLATED EARLY MESSAGES (3+ month gap) ==========
    if len(df) > 1:
        df = df.sort_values('datetime').reset_index(drop=True)
        first_msg_date = df['datetime'].iloc[0]
        three_months_later = first_msg_date + pd.DateOffset(months=3)
        #erase messages with date for first_msg 
        df = df[df['datetime'] != first_msg_date]
        
        # Check if there are messages within 3 months of the first message
        messages_within_3_months = df[df['datetime'] <= three_months_later]
        print(f"Messages within 3 months: {len(messages_within_3_months)}")
        
        if len(messages_within_3_months) == 1:
            # Only the first message exists within 3 months, find where activity resumes
            # Filter out all messages before the first message that has activity within 3 months
            for i in range(len(df)):
                current_date = df['datetime'].iloc[i]
                next_3_months = current_date + pd.DateOffset(months=3)
                messages_after = df[(df['datetime'] > current_date) & (df['datetime'] <= next_3_months)]
                
                if len(messages_after) > 0:
                    # Found a message with activity within 3 months, keep from here
                    df = df.iloc[i:].reset_index(drop=True)
                    app.logger.info(f"Filtered out {i} isolated early message(s) due to 3+ month gap")
                    break
    
    # Add helper columns
    df['day_of_week'] = df['datetime'].dt.dayofweek  # 0=Mon, 6=Sun
    df['day_name'] = df['datetime'].dt.day_name()
    df['hour'] = df['datetime'].dt.hour
    df['week_start'] = df['datetime'].dt.to_period('W-MON').dt.start_time
    
    # ========== 1. MESSAGES PER USER (for bar chart & pie chart) ==========
    sender_counts = df['sender'].value_counts()
    messages_per_user = [
        {"user": str(name), "count": int(count)}
        for name, count in sender_counts.items()
    ]
    
    # ========== 2. WEEKLY VOLUME (for line chart) ==========
    weekly = df.groupby('week_start').size().reset_index(name='count')
    weekly = weekly.sort_values('week_start')
    weekly_volume = [
        {
            "week": row['week_start'].strftime("%b '%y"),  # e.g., "Jan '24"
            "weekStart": row['week_start'].strftime('%b %d, %Y'),
            "count": int(row['count'])
        }
        for i, (_, row) in enumerate(weekly.iterrows())
    ]
    
    # ========== 3. HEATMAP DATA (7 days x 24 hours) ==========
    heatmap = df.groupby(['day_of_week', 'hour']).size().reset_index(name='count')
    heatmap_data = []
    for day in range(7):
        for hour in range(24):
            match = heatmap[(heatmap['day_of_week'] == day) & (heatmap['hour'] == hour)]
            count = int(match['count'].sum()) if not match.empty else 0
            heatmap_data.append({"day": day, "hour": hour, "count": count})
    
    # ========== 4. MESSAGE TYPE BREAKDOWN (for radar chart) ==========
    def count_emojis(text):
        if pd.isna(text):
            return 0
        return len([c for c in str(text) if c in emoji_lib.EMOJI_DATA])
    
    def is_media_message(text):
        if pd.isna(text):
            return False
        text = str(text).lower()
        media_patterns = ['<media omitted>', 'image omitted', 'video omitted', 
                         'audio omitted', 'sticker omitted', 'gif omitted',
                         '<mídia omitida>', '<media omesso>']
        return any(pattern in text for pattern in media_patterns)
    
    message_col = 'message' if 'message' in df.columns else 'content'
    message_type_breakdown = []
    
    for user in sender_counts.head(10).index:  # Top 10 users
        user_df = df[df['sender'] == user]
        user_messages = user_df[message_col] if message_col in user_df.columns else pd.Series([])
        
        total = len(user_df)
        media_count = sum(1 for msg in user_messages if is_media_message(msg))
        emoji_count = sum(count_emojis(msg) for msg in user_messages)
        text_count = total - media_count
        
        message_type_breakdown.append({
            "user": str(user),
            "text": int(text_count),
            "media": int(media_count),
            "emojis": int(emoji_count),
            "all": int(total)
        })
    
    # ========== 5. TOP EMOJIS (overall and per user) ==========
    def extract_emojis(text):
        """Extract all emojis from text as a list."""
        if pd.isna(text):
            return []
        return [c for c in str(text) if c in emoji_lib.EMOJI_DATA]
    
    # Count all emojis in the chat
    all_emojis = []
    for msg in df[message_col]:
        all_emojis.extend(extract_emojis(msg))
    
    # Get top 5 emojis overall
    emoji_counter = Counter(all_emojis)
    top_emojis_overall = [
        {"emoji": emoji, "count": count}
        for emoji, count in emoji_counter.most_common(5)
    ]
    
    # Get top 5 emojis per user
    top_emojis_per_user = []
    for user in sender_counts.index:  # All users
        user_messages = df[df['sender'] == user][message_col]
        user_emojis = []
        for msg in user_messages:
            user_emojis.extend(extract_emojis(msg))
        
        user_emoji_counter = Counter(user_emojis)
        top_5 = [
            {"emoji": emoji, "count": count}
            for emoji, count in user_emoji_counter.most_common(5)
        ]
        
        top_emojis_per_user.append({
            "user": str(user),
            "totalEmojis": len(user_emojis),
            "topEmojis": top_5
        })
    
    # ========== 6. DATE RANGE ==========
    date_range = {
        "startDate": df['datetime'].min().strftime('%b %d, %Y') if not df.empty else "N/A",
        "endDate": df['datetime'].max().strftime('%b %d, %Y') if not df.empty else "N/A"
    }
    
    # ========== 7. LONGEST EMOJI STREAK (consecutive days with emoji usage) ==========
    def calculate_longest_streak(dates):
        """Calculate longest streak of consecutive days."""
        if len(dates) == 0:
            return {"days": 0, "startDate": None, "endDate": None}
        
        # Normalize to dates (remove time component)
        unique_dates = sorted(set([pd.Timestamp(d).normalize() for d in dates]))
        
        if len(unique_dates) == 0:
            return {"days": 0, "startDate": None, "endDate": None}
        
        longest_streak = 1
        current_streak = 1
        streak_start = unique_dates[0]
        best_start = unique_dates[0]
        best_end = unique_dates[0]
        
        for i in range(1, len(unique_dates)):
            days_diff = (unique_dates[i] - unique_dates[i-1]).days
            
            if days_diff == 1:  # Consecutive day
                current_streak += 1
                if current_streak > longest_streak:
                    longest_streak = current_streak
                    best_start = streak_start
                    best_end = unique_dates[i]
            else:  # Gap in days
                current_streak = 1
                streak_start = unique_dates[i]
        
        return {
            "days": longest_streak,
            "startDate": best_start.strftime('%b %d, %Y') if longest_streak > 0 else None,
            "endDate": best_end.strftime('%b %d, %Y') if longest_streak > 0 else None
        }
    
    # Get dates where emojis were used (overall)
    emoji_dates = []
    for _, row in df.iterrows():
        msg = row.get(message_col, '')
        if not pd.isna(msg) and len(extract_emojis(msg)) > 0:
            emoji_dates.append(row['datetime'])
    
    # Overall longest emoji streak
    overall_streak = calculate_longest_streak(emoji_dates)
    
    # Longest emoji streak per user
    longest_streak_per_user = []
    for user in sender_counts.index:
        user_df = df[df['sender'] == user]
        user_emoji_dates = []
        for _, row in user_df.iterrows():
            msg = row.get(message_col, '')
            if not pd.isna(msg) and len(extract_emojis(msg)) > 0:
                user_emoji_dates.append(row['datetime'])
        
        user_streak = calculate_longest_streak(user_emoji_dates)
        longest_streak_per_user.append({
            "user": str(user),
            "days": user_streak["days"],
            "startDate": user_streak["startDate"],
            "endDate": user_streak["endDate"]
        })
    
    # ========== LEGACY: Daily messages (by day of week) ==========
    day_order = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
    day_short = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
    
    daily_counts = df['day_name'].value_counts()
    daily_messages = [
        {"day": day_short[i], "messages": int(daily_counts.get(day, 0))}
        for i, day in enumerate(day_order)
    ]
    
    # Most active day
    if not daily_counts.empty:
        most_active_day_full = daily_counts.idxmax()
        most_active_day = day_short[day_order.index(most_active_day_full)] if most_active_day_full in day_order else "Unknown"
    else:
        most_active_day = "Unknown"
    
    # ========== LEGACY: Hourly distribution ==========
    hourly_data = [
        {"hour": "0-6", "count": int(df[(df['hour'] >= 0) & (df['hour'] < 6)].shape[0])},
        {"hour": "6-12", "count": int(df[(df['hour'] >= 6) & (df['hour'] < 12)].shape[0])},
        {"hour": "12-18", "count": int(df[(df['hour'] >= 12) & (df['hour'] < 18)].shape[0])},
        {"hour": "18-24", "count": int(df[(df['hour'] >= 18) & (df['hour'] < 24)].shape[0])},
    ]
    
    # Peak hour
    hour_labels = ["12:00 AM - 6:00 AM", "6:00 AM - 12:00 PM", "12:00 PM - 6:00 PM", "6:00 PM - 12:00 AM"]
    peak_idx = max(range(4), key=lambda i: hourly_data[i]["count"])
    peak_hour = hour_labels[peak_idx]
    
    # ========== LEGACY: Participant data (top 4 + others) ==========
    colors = [
        "hsl(var(--chart-1))",
        "hsl(var(--chart-2))",
        "hsl(var(--chart-3))",
        "hsl(var(--chart-4))",
        "hsl(var(--chart-5))",
    ]
    
    participant_data = []
    for i, (name, count) in enumerate(sender_counts.head(4).items()):
        participant_data.append({
            "name": str(name),
            "value": int(count),
            "color": colors[i]
        })
    
    others_count = int(sender_counts.iloc[4:].sum()) if len(sender_counts) > 4 else 0
    if others_count > 0:
        participant_data.append({
            "name": "Others",
            "value": others_count,
            "color": colors[4]
        })
    
    return {
        # New chart data
        "messagesPerUser": messages_per_user,
        "weeklyVolume": weekly_volume,
        "heatmapData": heatmap_data,
        "messageTypeBreakdown": message_type_breakdown,
        "dateRange": date_range,
        # Emoji stats
        "topEmojisOverall": top_emojis_overall,
        "topEmojisPerUser": top_emojis_per_user,
        # Streak stats
        "longestStreak": overall_streak,
        "longestStreakPerUser": longest_streak_per_user,
        # Legacy chart data (for backward compatibility)
        "dailyMessages": daily_messages,
        "hourlyData": hourly_data,
        "participantData": participant_data,
        "mostActiveDay": most_active_day,
        "peakHour": peak_hour,
    }


@app.route("/analyze", methods=["POST"])
def analyze_zip():
    """
    Upload a WhatsApp chat ZIP file and get metrics + RAG session.
    
    Query params:
        - enable_rag: "true" to create RAG session (default: true)
    
    Returns:
        - All metrics/chart data
        - session_id for RAG queries (if enable_rag=true)
        - expires_in_seconds: TTL for RAG session
    """
    file = request.files.get("file")
    if file is None or not file.filename.endswith(".zip"):
        return jsonify({"error": "Invalid file"}), 400

    # Check if RAG should be enabled (default: false for speed)
    enable_rag = request.args.get("enable_rag", "false").lower() == "true"

    try:
        with tempfile.TemporaryDirectory() as tmpdir:
            # Save uploaded ZIP file
            zip_path = os.path.join(tmpdir, file.filename)
            file.save(zip_path)
            app.logger.info(f"Saved ZIP to: {zip_path}")

            # Extract ZIP file
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(tmpdir)
            app.logger.info(f"Extracted ZIP to: {tmpdir}")

            # Find the chat .txt file (WhatsApp exports are usually named _chat.txt)
            chat_files = glob.glob(os.path.join(tmpdir, "**", "*_chat.txt"), recursive=True)
            if not chat_files:
                # Try alternative patterns
                chat_files = glob.glob(os.path.join(tmpdir, "**", "*.txt"), recursive=True)
            
            if not chat_files:
                app.logger.error(f"No chat file found in ZIP. Contents: {os.listdir(tmpdir)}")
                return jsonify({"error": "No chat file found in ZIP"}), 400

            # Use the first chat file found
            chat_file_path = chat_files[0]
            app.logger.info(f"Found chat file: {chat_file_path}")
            
            # Parse the chat file using the existing parser
            try:
                parser = WhatsAppChatParser()
                df = parser.parse_to_csv(chat_file_path)
                app.logger.info(f"Parsed {len(df)} messages")
            except Exception as parse_error:
                app.logger.error(f"Error parsing chat file: {parse_error}")
                app.logger.error(traceback.format_exc())
                return jsonify({"error": f"Failed to parse chat file: {str(parse_error)}"}), 500
            
            if df.empty:
                app.logger.warning("DataFrame is empty after parsing")
                return jsonify({"error": "No messages found in chat file"}), 400

            # Compute basic statistics
            total_messages = len(df)
            participants = df['sender'].unique()
            participants_count = len(participants)
            
            app.logger.info(f"Total messages: {total_messages}, Participants: {participants_count}")
            
            # Find top user (user with most messages)
            sender_counts = df['sender'].value_counts()
            top_user = sender_counts.index[0] if not df.empty else "Unknown"
            
            # Get group name from parser
            group_title = parser.group_name if parser.group_name else "WhatsApp Chat"
            
            # Count emojis
            emoji_usage = {}
            if 'message' in df.columns:
                all_text = ' '.join(df['message'].astype(str))
                common_emojis = ['😂', '❤️', '👍', '😊', '😍', '🙏', '😭', '🔥', '💯', '🎉']
                for emoji in common_emojis:
                    count = all_text.count(emoji)
                    if count > 0:
                        emoji_usage[emoji] = count

            # Compute chart data
            chart_data = compute_chart_data(df)

            # ========== CREATE RAG SESSION ==========
            session_id = None
            expires_in_seconds = None
            
            if enable_rag:
                try:
                    session_id = create_rag_session(df)
                    expires_in_seconds = RAG_CACHE_TTL
                    app.logger.info(f"Created RAG session: {session_id}")
                except Exception as rag_error:
                    app.logger.warning(f"Failed to create RAG session: {rag_error}")
                    # Don't fail the whole request, just skip RAG

            # Build response with all data for frontend
            stats = {
                # ========== BASIC STATS ==========
                "total_messages": total_messages,
                "messageCount": total_messages,
                "participants_count": participants_count,
                "top_user": top_user,
                "title": group_title,
                "emojiUsage": emoji_usage,
                
                # ========== RAG SESSION ==========
                "session_id": session_id,
                "expires_in_seconds": expires_in_seconds,
                
                # ========== NEW CHART DATA ==========
                "messagesPerUser": chart_data["messagesPerUser"],
                "weeklyVolume": chart_data["weeklyVolume"],
                "heatmapData": chart_data["heatmapData"],
                "messageTypeBreakdown": chart_data["messageTypeBreakdown"],
                "dateRange": chart_data["dateRange"],
                
                # ========== EMOJI STATS ==========
                "topEmojisOverall": chart_data["topEmojisOverall"],
                "topEmojisPerUser": chart_data["topEmojisPerUser"],
                
                # ========== STREAK STATS ==========
                "longestStreak": chart_data["longestStreak"],
                "longestStreakPerUser": chart_data["longestStreakPerUser"],
                
                # ========== LEGACY CHART DATA (backward compatibility) ==========
                "dailyMessages": chart_data["dailyMessages"],
                "hourlyData": chart_data["hourlyData"],
                "participantData": chart_data["participantData"],
                "mostActiveDay": chart_data["mostActiveDay"],
                "peakHour": chart_data["peakHour"],
            }
            
            app.logger.info(f"Returning stats with {len(stats)} fields, RAG enabled: {enable_rag}")
            return jsonify(stats)
            
    except Exception as e:
        error_msg = f"Error processing file: {str(e)}"
        app.logger.error(error_msg)
        app.logger.error(traceback.format_exc())
        return jsonify({"error": error_msg}), 500


# ============================================================
# ALIAS ENDPOINTS (for frontend compatibility)
# ============================================================

@app.route("/metrics/chat/upload-anonymous", methods=["POST"])
def upload_anonymous():
    """Alias for /analyze - used by frontend GuestUpload."""
    return analyze_zip()


@app.route("/llm/analyze", methods=["POST"])
def llm_analyze():
    data = request.get_json()
    chat_id = data.get("chat_id")
    user_id = data.get("user_id")
    question = data.get("question")

    model = ChatOpenAI(
        model="gpt-4o-mini",
        api_key=os.getenv("OPENAI_API_KEY")
    )

    response = model.invoke(question)
    return jsonify({"answer": response.content})


if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5050,
        debug=True
    )
