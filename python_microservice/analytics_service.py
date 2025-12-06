from flask import Flask, request, jsonify
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

def compute_chart_data(df):
    """Compute all aggregated data for frontend charts."""
    
    # Ensure datetime column exists
    if 'datetime' not in df.columns and 'timestamp' in df.columns:
        df['datetime'] = pd.to_datetime(df['timestamp'], errors='coerce')
    elif 'datetime' in df.columns:
        df['datetime'] = pd.to_datetime(df['datetime'], errors='coerce')
    
    # Drop rows with invalid datetime
    df = df.dropna(subset=['datetime'])
    
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
            "week": f"W{i+1}",
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
    file = request.files.get("file")
    if file is None or not file.filename.endswith(".zip"):
        return jsonify({"error": "Invalid file"}), 400

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

            # Build response with all data for frontend
            stats = {
                # ========== BASIC STATS ==========
                "total_messages": total_messages,
                "messageCount": total_messages,
                "participants_count": participants_count,
                "top_user": top_user,
                "title": group_title,
                "emojiUsage": emoji_usage,
                
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
            
            app.logger.info(f"Returning stats with {len(stats)} fields")
            return jsonify(stats)
            
    except Exception as e:
        error_msg = f"Error processing file: {str(e)}"
        app.logger.error(error_msg)
        app.logger.error(traceback.format_exc())
        return jsonify({"error": error_msg}), 500

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
        ssl_context=("certs/cert.pem", "certs/key.pem"),
        debug=True
    )
