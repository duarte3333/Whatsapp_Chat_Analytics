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
from langchain_openai import OpenAIEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document

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


def create_rag_session(df, session_id=None):
    """Create a RAG session from chat DataFrame with TTL expiration."""
    
    if session_id is None:
        session_id = str(uuid.uuid4())
    
    # Convert messages to documents
    message_col = 'message' if 'message' in df.columns else 'content'
    documents = []
    
    for _, row in df.iterrows():
        # Create document with metadata
        content = f"[{row.get('datetime', 'Unknown')}] {row['sender']}: {row.get(message_col, '')}"
        doc = Document(
            page_content=content,
            metadata={
                "sender": row['sender'],
                "datetime": str(row.get('datetime', '')),
            }
        )
        documents.append(doc)
    
    # Split into chunks for better retrieval
    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=1000,
        chunk_overlap=100,
        separators=["\n\n", "\n", " "]
    )
    splits = text_splitter.split_documents(documents)
    
    # Create embeddings and vector store
    embeddings = OpenAIEmbeddings(api_key=os.getenv("OPENAI_API_KEY"))
    vectorstore = FAISS.from_documents(splits, embeddings)
    
    # Store in cache with TTL
    with rag_cache_lock:
        rag_cache[session_id] = {
            "vectorstore": vectorstore,
            "created_at": time.time(),
            "message_count": len(df),
            "participants": list(df['sender'].unique()),
        }
    
    app.logger.info(f"Created RAG session {session_id} with {len(splits)} chunks (expires in {RAG_CACHE_TTL}s)")
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
    """Ask a question about an uploaded chat using RAG."""
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
        vectorstore = session["vectorstore"]
        
        # Get total number of documents in the vectorstore
        total_docs = vectorstore.index.ntotal
        
        # Use all docs if less than 150, otherwise cap at 150
        k = min(total_docs, 250)
        app.logger.info(f"RAG query: total_docs={total_docs}, using k={k}")
        
        # Retrieve relevant documents
        retriever = vectorstore.as_retriever(search_kwargs={"k": k})
        relevant_docs = retriever.invoke(question)
        
        # Build context from retrieved documents
        context = "\n".join([doc.page_content for doc in relevant_docs])
        
        # Create prompt with context
        #    - ANSWER IN THE SAME LANGUAGE AS THE CHAT, check variations(e.g PT PT is not PT BR) 
        
        prompt = f"""You are analyzing this WhatsApp group chat.

        CHAT MESSAGES:
        {context}

        QUESTION: {question}

        CRITICAL ANALYSIS METHOD (apply to ANY personality question):

        1. REACTION PRINCIPLE:
        - When analyzing WHO has a trait (funny, charming, smart, annoying, etc.)
        - Look at how OTHERS REACT to that person's messages
        - The person who TRIGGERS positive/negative reactions = has the trait
        - The person who REACTS = is just responding, doesn't have the trait

        3. WHAT TO AVOID:
        - Always trace back: WHO caused the reaction?

        4. EVIDENCE RULE:
        - Quote the ORIGINAL message that triggered reactions
        - Show the reactions it received as proof
        - Count who gets the MOST reactions of the relevant type

        YOUR STYLE:
        - Be RATIONAL and CONVERSATIONAL
        - Keep it SHORT (2-4 sentences max, unless they ask for details)
        - Quote specific messages as evidence
        - NO bullet points, NO formal structure
        - Answer in English!

        NOW ANSWER (trace who CAUSES reactions, not who GIVES them):"""

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
            "sources_count": len(relevant_docs),
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
    
    # ========== 5. DATE RANGE ==========
    date_range = {
        "startDate": df['datetime'].min().strftime('%b %d, %Y') if not df.empty else "N/A",
        "endDate": df['datetime'].max().strftime('%b %d, %Y') if not df.empty else "N/A"
    }
    
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
