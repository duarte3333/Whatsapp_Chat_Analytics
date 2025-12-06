from flask import Flask, request, jsonify
import zipfile
import tempfile
import os
import sys
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
import glob
import traceback

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

            # Compute statistics
            total_messages = len(df)
            participants = df['sender'].unique()
            participants_count = len(participants)
            
            app.logger.info(f"Total messages: {total_messages}, Participants: {participants_count}")
            app.logger.info(f"Participants: {list(participants)}")
            
            # Find top user (user with most messages)
            sender_counts = df['sender'].value_counts()
            app.logger.info(f"Sender counts: {sender_counts.to_dict()}")
            top_user = sender_counts.index[0] if not df.empty else "Unknown"
            
            # Get group name from parser
            group_title = parser.group_name if parser.group_name else "WhatsApp Chat"
            
            # Count emojis (basic implementation - you can enhance this)
            emoji_usage = {}
            if 'message' in df.columns:
                # Simple emoji counting (you might want to use a proper emoji library)
                all_text = ' '.join(df['message'].astype(str))
                # Count common emojis (this is a simplified version)
                common_emojis = ['😂', '❤️', '👍', '😊', '😍', '🙏', '😭', '🔥', '💯', '🎉']
                for emoji in common_emojis:
                    count = all_text.count(emoji)
                    if count > 0:
                        emoji_usage[emoji] = count

            # Build response matching what Java backend expects
            stats = {
                "total_messages": total_messages,
                "messageCount": total_messages,  # Alias for compatibility
                "participants_count": participants_count,
                "top_user": top_user,
                "title": group_title,
                "emojiUsage": emoji_usage
                # DO NOT include full messages array - it's too large!
                # "messages": df.to_dict('records')  # REMOVE THIS LINE
            }
            
            app.logger.info(f"Returning stats: total_messages={total_messages}, participants={participants_count}, top_user={top_user}")
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
