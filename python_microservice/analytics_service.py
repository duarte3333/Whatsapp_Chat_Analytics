from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
import zipfile
import tempfile
import os
import sys
import re
import json
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
import glob
import traceback
import pandas as pd
import numpy as np
import emoji as emoji_lib
import uuid
import time
from threading import Lock
from cachetools import TTLCache
from collections import Counter
import tiktoken

# Import security validator
from security_validator import (
    validate_file_upload, 
    safe_extract_zip, 
    sanitize_text_input,
    is_valid_filename
)

# Add stats_computation directory to Python path to import parser
stats_computation_path = os.path.join(os.path.dirname(__file__), '..', 'stats_computation')
if stats_computation_path not in sys.path:
    sys.path.insert(0, stats_computation_path)

# Now import the parser and its dependencies
try:
    from parser import WhatsAppChatParser
except ImportError as e:
    traceback.print_exc()
    raise

load_dotenv()

app = Flask(__name__)
CORS(app, resources={r"/*": {"origins": "*"}})
app.json.ensure_ascii = False

# Configure Flask to always return JSON errors
app.config['JSONIFY_PRETTYPRINT_REGULAR'] = False
app.config['PROPAGATE_EXCEPTIONS'] = True  # Allow error handlers to work

# Rate limiting configuration
limiter = Limiter(
    app=app,
    key_func=get_remote_address,
    default_limits=["200 per hour", "30 per minute"],
    storage_uri="memory://"  # In-memory storage (use Redis in production)
)

# Middleware to ensure JSON responses for all errors
@app.after_request
def after_request(response):
    """Ensure error responses are JSON."""
    if response.status_code >= 400:
        # If response is HTML, convert to JSON
        if 'text/html' in response.content_type:
            try:
                error_data = {
                    "error": f"HTTP {response.status_code}",
                    "message": "An error occurred processing the request"
                }
                response = jsonify(error_data)
                response.status_code = 500
            except Exception:
                pass
    return response

# Helper function to convert NumPy/Pandas types to Python native types
def make_json_serializable(obj):
    """Recursively convert NumPy/Pandas types to Python native types."""
    
    if isinstance(obj, (np.integer, np.int64, np.int32)):
        return int(obj)
    elif isinstance(obj, (np.floating, np.float64, np.float32)):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, pd.Series):
        return obj.tolist()
    elif isinstance(obj, pd.Timestamp):
        return obj.isoformat()
    elif isinstance(obj, dict):
        return {k: make_json_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, (list, tuple)):
        return [make_json_serializable(item) for item in obj]
    elif pd.isna(obj):
        return None
    else:
        return obj

# Global error handler to ensure JSON responses (only for unhandled errors)
@app.errorhandler(500)
def handle_500_error(e):
    """Handle 500 errors and return JSON."""
    error_msg = str(e) if e else "Internal Server Error"
    error_traceback = traceback.format_exc()
    app.logger.error(f"500 error: {error_msg}")
    app.logger.error(error_traceback)
    
    # Always return JSON, even for unhandled errors
    try:
        error_response = {
            "error": error_msg,
            "details": error_traceback if app.debug else "Check server logs for details"
        }
        error_response_serializable = make_json_serializable(error_response)
        return jsonify(error_response_serializable), 500
    except Exception as json_error:
        # Fallback: return plain JSON string
        app.logger.error(f"Failed to jsonify error response: {json_error}")
        from flask import Response
        return Response(
            '{"error": "Internal server error occurred"}',
            mimetype='application/json',
            status=500
        )

# Handle all exceptions - MUST be registered before routes
@app.errorhandler(Exception)
def handle_all_exceptions(e):
    """Catch all exceptions and return JSON."""
    error_msg = str(e) if e else "Unknown error"
    error_traceback = traceback.format_exc()
    
    try:
        app.logger.error(f"Unhandled exception: {error_msg}")
        app.logger.error(error_traceback)
    except:
        pass  # Don't fail if logger doesn't work
    
    try:
        error_response = {
            "error": error_msg,
            "details": error_traceback if app.debug else "Check server logs for details"
        }
        error_response_serializable = make_json_serializable(error_response)
        response = jsonify(error_response_serializable)
        response.status_code = 500
        return response
    except Exception as json_error:
        from flask import Response
        return Response(
            json.dumps({"error": "Internal server error occurred"}),
            mimetype='application/json',
            status=500
        )

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
    
    # ========== OPTIMIZATION: Vectorized message formatting ==========
    # Use vectorized string operations instead of iterrows()
    formatted_messages = (
        "[" + df['datetime'].astype(str) + "] " + 
        df['sender'].astype(str) + ": " + 
        df[message_col].fillna('').astype(str)
    ).tolist()
    
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
            "conversation_history": [],  # Store Q&A pairs for conversation context
        }
    
    return session_id


def get_rag_session(session_id):
    """Get RAG session if it exists and hasn't expired."""
    with rag_cache_lock:
        return rag_cache.get(session_id)


# ============================================================
# NEW ENDPOINTS FOR EPHEMERAL RAG
# ============================================================

@app.route("/rag/upload", methods=["POST"])
@limiter.limit("3 per hour")  # Very strict for file uploads
def rag_upload():
    """Upload a chat file (ZIP or TXT) for ephemeral RAG. Returns session_id valid for 10 minutes."""
    file = request.files.get("file")
    if file is None:
        return jsonify({"error": "No file provided"}), 400
    
    filename = file.filename
    if not filename:
        return jsonify({"error": "Invalid filename"}), 400
    
    # Security validation
    file_size = file.content_length if hasattr(file, 'content_length') else 0
    validation_error = validate_file_upload(filename, file_size)
    if validation_error:
        app.logger.warning(f"File validation failed: {validation_error}")
        return jsonify({"error": validation_error}), 400
    
    filename_lower = filename.lower()
    if not (filename_lower.endswith(".zip") or filename_lower.endswith(".txt")):
        return jsonify({"error": "File must be a ZIP file or a TXT file"}), 400

    try:
        with tempfile.TemporaryDirectory() as tmpdir:
            chat_file_path = None
            
            # Handle TXT files directly
            if filename_lower.endswith(".txt"):
                # Sanitize filename before saving
                safe_filename = is_valid_filename(filename) and filename or "chat.txt"
                txt_path = os.path.join(tmpdir, safe_filename)
                file.save(txt_path)
                chat_file_path = txt_path
            else:
                # Handle ZIP files (extract and find chat file)
                safe_filename = is_valid_filename(filename) and filename or "chat.zip"
                zip_path = os.path.join(tmpdir, safe_filename)
                file.save(zip_path)
                
                # Safe extraction with validation
                error_msg, chat_file_path = safe_extract_zip(zip_path, tmpdir)
                if error_msg:
                    app.logger.warning(f"ZIP extraction failed: {error_msg}")
                    return jsonify({"error": error_msg}), 400
                
                if not chat_file_path:
                    # Fallback: find chat file
                    chat_files = glob.glob(os.path.join(tmpdir, "**", "*_chat.txt"), recursive=True)
                    if not chat_files:
                        chat_files = glob.glob(os.path.join(tmpdir, "**", "*.txt"), recursive=True)
                    if not chat_files:
                        return jsonify({"error": "No chat file found in ZIP"}), 400
                    chat_file_path = chat_files[0]

            # Parse chat
            parser = WhatsAppChatParser()
            df = parser.parse_to_csv(chat_file_path)
            
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
# Uses default_limits: 30 per minute, 200 per hour
def rag_ask():
    """Ask a question about an uploaded chat using the most recent messages."""
    data = request.get_json()
    session_id = data.get("session_id")
    question = data.get("question")

    if not session_id or not question:
        return jsonify({"error": "session_id and question are required"}), 400
    
    # Sanitize inputs to prevent injection
    session_id = str(session_id).strip()[:100]  # Limit length
    question = sanitize_text_input(str(question), max_length=5000)  # Limit and sanitize
    
    if not question:
        return jsonify({"error": "Question cannot be empty"}), 400

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
        conversation_history = session.get("conversation_history", [])
        
        # Build conversation history string if there are previous Q&A pairs
        history_text = ""
        if conversation_history:
            history_text = "\n\nPREVIOUS CONVERSATION:\n"
            for i, (prev_q, prev_a) in enumerate(conversation_history, 1):
                history_text += f"Q{i}: {prev_q}\nA{i}: {prev_a}\n"
            history_text += "\n"
        
        prompt = f"""
        You are an expert conversation analyst analyzing a WhatsApp chat.

        Your task is to answer the user's question using ONLY the chat messages provided.
        Do NOT use outside knowledge. Do NOT guess.
        {history_text}
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
        answer_content = response.content
        
        # Save Q&A pair to conversation history
        with rag_cache_lock:
            if session_id in rag_cache:
                if "conversation_history" not in rag_cache[session_id]:
                    rag_cache[session_id]["conversation_history"] = []
                rag_cache[session_id]["conversation_history"].append((question, answer_content))
        
        # Calculate remaining TTL
        time_elapsed = time.time() - session["created_at"]
        time_remaining = max(0, RAG_CACHE_TTL - time_elapsed)
        
        # Get updated conversation history for response
        updated_session = get_rag_session(session_id)
        conversation_history = updated_session.get("conversation_history", []) if updated_session else []

        return jsonify({
            "answer": answer_content,
            "messages_used": included_messages,
            "total_messages": total_messages,
            "session_id": session_id,
            "expires_in_seconds": int(time_remaining),
            "conversation_history": [
                {"question": q, "answer": a} 
                for q, a in conversation_history
            ],
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
    
    conversation_history = session.get("conversation_history", [])
    
    return jsonify({
        "valid": True,
        "session_id": session_id,
        "message_count": session["message_count"],
        "included_messages": session["included_messages"],
        "context_tokens": session["context_tokens"],
        "participants": session["participants"],
        "expires_in_seconds": int(time_remaining),
        "conversation_history": [
            {"question": q, "answer": a} 
            for q, a in conversation_history
        ],
    })


def compute_chart_data(df):
    """Compute all aggregated data for frontend charts."""
    
    # Helper function to convert NumPy types to Python native types
    def to_python_int(value):
        """Convert NumPy int64/int32 to Python int."""
        if pd.isna(value):
            return 0
        return int(value)
    
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
                    break
    
    # Add helper columns
    df['day_of_week'] = df['datetime'].dt.dayofweek  # 0=Mon, 6=Sun
    df['day_name'] = df['datetime'].dt.day_name()
    df['hour'] = df['datetime'].dt.hour
    df['week_start'] = df['datetime'].dt.to_period('W-MON').dt.start_time
    
    # Helper function to detect media messages across multiple languages
    def is_media_message(text):
        if pd.isna(text):
            return False
        text = str(text).lower()
        
        # Comprehensive list of media message patterns across multiple languages
        # WhatsApp uses different patterns for media messages in different languages
        media_patterns = [
            # English
            '<media omitted>', 'media omitted', 'image omitted', 'video omitted',
            'audio omitted', 'sticker omitted', 'gif omitted', 'document omitted',
            'voice message omitted', 'location omitted', 'contact omitted',
            
            # Portuguese (WhatsApp uses "não revelada/não revelado")
            'imagem não revelada', 'vídeo não revelado', 'áudio não revelado',
            'ficheiro de áudio não revelado', 'documento não revelado',
            'contacto não revelado', 'localização não revelada',
            'figurinha não revelada', 'gif não revelado', 'mídia não revelada',
            # Also include older/alternative patterns
            '<mídia omitida>', 'mídia omitida', 'imagem omitida', 'vídeo omitido',
            'áudio omitido', 'figurinha omitida', 'gif omitido', 'documento omitido',
            'mensagem de voz omitida', 'localização omitida', 'contato omitido',
            
            # Spanish
            '<multimedia omitida>', 'multimedia omitida', 'imagen omitida', 'vídeo omitido',
            'audio omitido', 'pegatina omitida', 'gif omitido', 'documento omitido',
            'mensaje de voz omitido', 'ubicación omitida', 'contacto omitido',
            
            # French
            '<média omis>', 'média omis', 'image omise', 'vidéo omise',
            'audio omis', 'autocollant omis', 'gif omis', 'document omis',
            'message vocal omis', 'localisation omise', 'contact omis',
            
            # German
            '<medien ausgelassen>', 'medien ausgelassen', 'bild ausgelassen',
            'video ausgelassen', 'audio ausgelassen', 'sticker ausgelassen',
            'gif ausgelassen', 'dokument ausgelassen', 'sprachnachricht ausgelassen',
            'standort ausgelassen', 'kontakt ausgelassen',
            
            # Italian
            '<media omesso>', 'media omesso', 'immagine omessa', 'video omesso',
            'audio omesso', 'adesivo omesso', 'gif omesso', 'documento omesso',
            'messaggio vocale omesso', 'posizione omessa', 'contatto omesso',
            
            # Dutch
            '<media weggelaten>', 'media weggelaten', 'afbeelding weggelaten',
            'video weggelaten', 'audio weggelaten', 'sticker weggelaten',
            'gif weggelaten', 'document weggelaten', 'spraakbericht weggelaten',
            'locatie weggelaten', 'contact weggelaten',
            
            # Russian (Cyrillic)
            'медиафайл не включён', 'изображение не включено', 'видео не включено',
            'аудио не включено', 'стикер не включён', 'gif не включён',
            'документ не включён', 'голосовое сообщение не включено',
            
            # Arabic
            'تم حذف الوسائط', 'تم حذف الصورة', 'تم حذف الفيديو',
            'تم حذف الصوت', 'تم حذف الملصق', 'تم حذف الملف',
            
            # Hindi (Devanagari)
            'मीडिया हटाया गया', 'छवि हटाई गई', 'वीडियो हटाया गया',
            'ऑडियो हटाया गया', 'स्टिकर हटाया गया', 'दस्तावेज़ हटाया गया',
            
            # Chinese (Simplified) - more specific patterns
            '媒体文件已省略', '图片已省略', '视频已省略', '音频已省略',
            '贴纸已省略', '文件已省略', '媒体未包含',
            
            # Japanese - specific omission patterns
            'メディアが省略されました', '画像が省略されました', '動画が省略されました',
            '音声が省略されました', 'ステッカーが省略されました', 'ファイルが省略されました',
            
            # Turkish
            'medya atlandı', 'görsel atlandı', 'video atlandı',
            'ses atlandı', 'çıkartma atlandı', 'dosya atlandı',
            
            # Polish
            'pominięto multimedia', 'pominięto obraz', 'pominięto wideo',
            'pominięto audio', 'pominięto naklejkę', 'pominięto plik',
            
            # Additional common patterns (keeping specific to avoid false positives)
            'file omitted', 'document omitted', 'attachment omitted'
        ]
        
        return any(pattern in text for pattern in media_patterns)
    
    # Helper function to detect if a message contains at least one emoji
    def has_emoji(text):
        if pd.isna(text):
            return False
        text_str = str(text)
        # Use emoji_list() to properly detect emojis with skin tone modifiers
        return len(emoji_lib.emoji_list(text_str)) > 0
    
    # Helper function to detect audio/voice messages specifically
    def is_audio_message(text):
        if pd.isna(text):
            return False
        text = str(text).lower()
        audio_patterns = [
            # English
            'audio omitted', '<audio omitted>', 'voice omitted', 'voice message omitted',
            'ptt omitted',  # WhatsApp internal for voice notes (Push To Talk)
            
            # Portuguese
            'áudio omitido', 'áudio não revelado', 'audio não revelado',
            'ficheiro de áudio não revelado', 'mensagem de voz omitida',
            'mensagem de voz não revelada',
            
            # Spanish
            'audio omitido', 'audio no revelado', 'mensaje de voz omitido',
            'mensaje de voz no revelado',
            
            # French
            'audio omis', 'message vocal omis',
            
            # German
            'audio ausgelassen', 'sprachnachricht ausgelassen',
            
            # Italian
            'audio omesso', 'messaggio vocale omesso',
            
            # Dutch
            'audio weggelaten', 'spraakbericht weggelaten',
            
            # Russian
            'аудио не включено', 'голосовое сообщение не включено',
            
            # Arabic
            'تم حذف الصوت',
            
            # Hindi
            'ऑडियो हटाया गया',
            
            # Chinese
            '音频已省略', '语音消息已省略',
            
            # Japanese
            '音声が省略されました',
            
            # Turkish
            'ses atlandı',
            
            # Polish
            'pominięto audio'
        ]
        return any(pattern in text for pattern in audio_patterns)
    
    # Helper function to detect sticker messages specifically
    def is_sticker_message(text):
        if pd.isna(text):
            return False
        text = str(text).lower()
        sticker_patterns = [
            # English
            'sticker omitted', '<sticker omitted>',
            
            # Portuguese
            'figurinha não revelada', 'figurinha omitida',
            'sticker não revelado',  # Portuguese (Portugal)
            
            # Spanish
            'pegatina omitida', 'pegatina no revelada',
            
            # French
            'autocollant omis', '<autocollant omis>',
            
            # German
            'sticker ausgelassen', '<sticker ausgelassen>',
            
            # Italian
            'adesivo omesso', '<adesivo omesso>',
            
            # Dutch
            'sticker weggelaten', '<sticker weggelaten>',
            
            # Russian
            'стикер не включён', 'стикер не включен',
            
            # Arabic
            'تم حذف الملصق',
            
            # Hindi
            'स्टिकर हटाया गया',
            
            # Chinese
            '贴纸已省略', '贴纸未包含',
            
            # Japanese
            'ステッカーが省略されました',
            
            # Turkish
            'çıkartma atlandı',
            
            # Polish
            'pominięto naklejkę'
        ]
        return any(pattern in text for pattern in sticker_patterns)
    
    message_col = 'message' if 'message' in df.columns else 'content'
    
    # ========== OPTIMIZATION: Pre-compute helper columns once ==========
    # This avoids calling functions repeatedly in loops
    app.logger.info("Pre-computing helper columns...")
    df['_is_media'] = df[message_col].apply(is_media_message)
    df['_has_emoji'] = df[message_col].apply(has_emoji)
    df['_is_audio'] = df[message_col].apply(is_audio_message)
    df['_is_sticker'] = df[message_col].apply(is_sticker_message)
    df['_is_morning'] = (df['hour'] >= 6) & (df['hour'] < 12)
    df['_is_afternoon'] = (df['hour'] >= 12) & (df['hour'] < 18)
    df['_is_night'] = (df['hour'] >= 18) | (df['hour'] < 6)
    df['_is_weekend'] = df['day_of_week'] >= 5
    
    # ========== 1. MESSAGES PER USER (for bar chart & pie chart) ==========
    sender_counts = df['sender'].value_counts()
    messages_per_user = []
    
    # Use groupby for efficient aggregation instead of filtering in loops
    for name, total_count in sender_counts.items():
        # Convert NumPy types to Python native types
        name = str(name)  # Ensure name is string
        total_count = int(total_count)  # Convert numpy.int64 to Python int
        
        user_mask = df['sender'] == name
        user_df = df[user_mask]
        
        # Use pre-computed columns for fast counting
        # Convert all NumPy types to Python native types for JSON serialization
        media_count = int(user_df['_is_media'].sum())
        text_count = total_count - media_count
        emoji_count = int(user_df['_has_emoji'].sum())
        audio_count = int(user_df['_is_audio'].sum())
        sticker_count = int(user_df['_is_sticker'].sum())
        morning_count = int(user_df['_is_morning'].sum())
        afternoon_count = int(user_df['_is_afternoon'].sum())
        night_count = int(user_df['_is_night'].sum())
        weekend_count = int(user_df['_is_weekend'].sum())
        
        messages_per_user.append({
            "user": str(name),
            "count": int(total_count),
            "textCount": text_count,
            "mediaCount": media_count,
            "emojiCount": emoji_count,
            "audioCount": audio_count,
            "stickerCount": sticker_count,
            "morningCount": morning_count,
            "afternoonCount": afternoon_count,
            "nightCount": night_count,
            "weekendCount": weekend_count
        })
    
    # ========== 2. WEEKLY VOLUME (for line chart) ==========
    weekly = df.groupby('week_start').size().reset_index(name='count')
    weekly = weekly.sort_values('week_start')
    # Optimized: use list comprehension without iterrows()
    weekly_volume = [
        {
            "week": week_start.strftime("%b '%y"),  # e.g., "Jan '24"
            "weekStart": week_start.strftime('%b %d, %Y'),
            "count": to_python_int(count)
        }
        for week_start, count in zip(weekly['week_start'], weekly['count'])
    ]
    
    # ========== 3. HEATMAP DATA (7 days x 24 hours) ==========
    heatmap = df.groupby(['day_of_week', 'hour']).size().reset_index(name='count')
    heatmap_data = []
    for day in range(7):
        for hour in range(24):
            match = heatmap[(heatmap['day_of_week'] == day) & (heatmap['hour'] == hour)]
            count = to_python_int(match['count'].sum()) if not match.empty else 0
            heatmap_data.append({"day": day, "hour": hour, "count": count})
    
    # ========== 4. MESSAGE TYPE BREAKDOWN (for radar chart) ==========
    def count_emojis(text):
        """Count emojis in text, including those with skin tone modifiers."""
        if pd.isna(text):
            return 0
        # Use emoji_list() to properly count emojis with modifiers as single units
        return len(emoji_lib.emoji_list(str(text)))
    
    # ========== OPTIMIZATION: Use pre-computed columns and vectorized operations ==========
    message_type_breakdown = []
    
    for user in sender_counts.head(10).index:  # Top 10 users
        user_mask = df['sender'] == user
        user_df = df[user_mask]
        
        total = len(user_df)
        media_count = int(user_df['_is_media'].sum())
        # Count emojis using vectorized operation
        emoji_count = int(user_df[message_col].apply(count_emojis).sum())
        text_count = total - media_count
        
        message_type_breakdown.append({
            "user": str(user),
            "text": int(text_count),
            "media": media_count,
            "emojis": emoji_count,
            "all": int(total)
        })
    
    # ========== 5. TOP EMOJIS (overall and per user) ==========
    def extract_emojis(text):
        """Extract all emojis from text as a list, preserving skin tone modifiers."""
        if pd.isna(text):
            return []
        
        text_str = str(text)
        emojis = []
        
        # Use emoji_list() to properly extract emojis with skin tone modifiers
        # This preserves multi-character emojis (base + modifier) as single units
        emoji_list = emoji_lib.emoji_list(text_str)
        
        for emoji_info in emoji_list:
            # Extract the emoji character(s) including modifiers
            emoji_char = emoji_info['emoji']
            emojis.append(emoji_char)
        
        return emojis
    
    # ========== OPTIMIZATION: Vectorized emoji extraction ==========
    app.logger.info("Extracting emojis (vectorized)...")
    # Extract all emojis at once using apply (much faster than loops)
    all_emoji_lists = df[message_col].apply(extract_emojis)
    # Flatten list of lists
    all_emojis = [emoji for emoji_list in all_emoji_lists for emoji in emoji_list]
    
    # Get top 5 emojis overall
    emoji_counter = Counter(all_emojis)
    top_emojis_overall = [
        {"emoji": emoji, "count": to_python_int(count)}
        for emoji, count in emoji_counter.most_common(5)
    ]
    
    # Get top 5 emojis per user (optimized with groupby)
    top_emojis_per_user = []
    for user in sender_counts.index:  # All users
        user_mask = df['sender'] == user
        user_emoji_lists = df.loc[user_mask, message_col].apply(extract_emojis)
        user_emojis = [emoji for emoji_list in user_emoji_lists for emoji in emoji_list]
        
        user_emoji_counter = Counter(user_emojis)
        top_5 = [
            {"emoji": emoji, "count": to_python_int(count)}
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
    
    # ========== OPTIMIZATION: Use pre-computed _has_emoji column ==========
    # Get dates where emojis were used (overall) - use vectorized operation
    emoji_dates = df.loc[df['_has_emoji'], 'datetime'].tolist()
    
    # Overall longest emoji streak
    overall_streak = calculate_longest_streak(emoji_dates)
    
    # Longest emoji streak per user (optimized)
    longest_streak_per_user = []
    for user in sender_counts.index:
        user_mask = (df['sender'] == user) & df['_has_emoji']
        user_emoji_dates = df.loc[user_mask, 'datetime'].tolist()
        
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
        {"day": day_short[i], "messages": to_python_int(daily_counts.get(day, 0))}
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
        {"hour": "0-6", "count": to_python_int(df[(df['hour'] >= 0) & (df['hour'] < 6)].shape[0])},
        {"hour": "6-12", "count": to_python_int(df[(df['hour'] >= 6) & (df['hour'] < 12)].shape[0])},
        {"hour": "12-18", "count": to_python_int(df[(df['hour'] >= 12) & (df['hour'] < 18)].shape[0])},
        {"hour": "18-24", "count": to_python_int(df[(df['hour'] >= 18) & (df['hour'] < 24)].shape[0])},
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
            "value": to_python_int(count),
            "color": colors[i]
        })
    
    others_count = to_python_int(sender_counts.iloc[4:].sum()) if len(sender_counts) > 4 else 0
    if others_count > 0:
        participant_data.append({
            "name": "Others",
            "value": others_count,
            "color": colors[4]
        })
    
    # ========== Cleanup: Remove temporary helper columns ==========
    df.drop(columns=['_is_media', '_has_emoji', '_is_audio', '_is_sticker', 
                     '_is_morning', '_is_afternoon', '_is_night', '_is_weekend'], 
            errors='ignore', inplace=True)
    
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
@limiter.limit("3 per hour")  # Very strict for file uploads
def analyze_zip():
    """
    Upload a WhatsApp chat file (ZIP or TXT) and get metrics + RAG session.
    
    Query params:
        - enable_rag: "true" to create RAG session (default: false)
    
    Returns:
        - All metrics/chart data
        - session_id for RAG queries (if enable_rag=true)
        - expires_in_seconds: TTL for RAG session
    """
    try:
        app.logger.info("=== /analyze endpoint called ===")
        
        file = request.files.get("file")
        if file is None:
            app.logger.warning("No file provided in request")
            return jsonify({"error": "No file provided"}), 400
        
        filename = file.filename
        if not filename:
            return jsonify({"error": "Invalid filename"}), 400
        
        # Security validation
        file_size = file.content_length if hasattr(file, 'content_length') else 0
        validation_error = validate_file_upload(filename, file_size)
        if validation_error:
            app.logger.warning(f"File validation failed: {validation_error}")
            return jsonify({"error": validation_error}), 400
        
        filename_lower = filename.lower()
        app.logger.info(f"Processing file: {filename}, size: {file_size}")
        
        if not (filename_lower.endswith(".zip") or filename_lower.endswith(".txt")):
            app.logger.warning(f"Invalid file type: {filename}")
            return jsonify({"error": "File must be a ZIP file or a TXT file"}), 400

        # Check if RAG should be enabled (default: false for speed)
        enable_rag = request.args.get("enable_rag", "false").lower() == "true"
        app.logger.info(f"RAG enabled: {enable_rag}")
        with tempfile.TemporaryDirectory() as tmpdir:
            chat_file_path = None
            
            # Handle TXT files directly
            if filename_lower.endswith(".txt"):
                # Sanitize filename before saving
                safe_filename = is_valid_filename(filename) and filename or "chat.txt"
                txt_path = os.path.join(tmpdir, safe_filename)
                file.save(txt_path)
                chat_file_path = txt_path
            else:
                # Handle ZIP files (extract and find chat file)
                safe_filename = is_valid_filename(filename) and filename or "chat.zip"
                zip_path = os.path.join(tmpdir, safe_filename)
                file.save(zip_path)

                # Safe extraction with validation
                error_msg, chat_file_path = safe_extract_zip(zip_path, tmpdir)
                if error_msg:
                    app.logger.warning(f"ZIP extraction failed: {error_msg}")
                    return jsonify({"error": error_msg}), 400
                
                if not chat_file_path:
                    # Fallback: find chat file
                    chat_files = glob.glob(os.path.join(tmpdir, "**", "*_chat.txt"), recursive=True)
                    if not chat_files:
                        chat_files = glob.glob(os.path.join(tmpdir, "**", "*.txt"), recursive=True)
                    if not chat_files:
                        app.logger.error(f"No chat file found in ZIP. Contents: {os.listdir(tmpdir)}")
                        return jsonify({"error": "No chat file found in ZIP"}), 400
                    chat_file_path = chat_files[0]
            
            # Parse the chat file using the existing parser
            try:
                parser = WhatsAppChatParser()
                df = parser.parse_to_csv(chat_file_path)
            except Exception as parse_error:
                app.logger.error(f"Error parsing chat file: {parse_error}")
                app.logger.error(traceback.format_exc())
                return jsonify({"error": f"Failed to parse chat file: {str(parse_error)}"}), 500
            
            if df.empty:
                app.logger.warning("DataFrame is empty after parsing")
                return jsonify({"error": "No messages found in chat file"}), 400

            # Compute basic statistics
            total_messages = int(len(df))  # Ensure Python int
            participants = df['sender'].unique()
            participants_count = int(len(participants))  # Ensure Python int
            
            # Find top user (user with most messages)
            sender_counts = df['sender'].value_counts()
            top_user = str(sender_counts.index[0]) if not df.empty else "Unknown"
            
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
            try:
                app.logger.info("Computing chart data...")
                chart_data = compute_chart_data(df)
                app.logger.info("Chart data computed successfully")
            except Exception as chart_error:
                app.logger.error(f"Error computing chart data: {chart_error}")
                app.logger.error(traceback.format_exc())
                # Return empty/default chart data instead of failing
                chart_data = {
                    "messagesPerUser": [],
                    "weeklyVolume": [],
                    "heatmapData": [],
                    "messageTypeBreakdown": [],
                    "dateRange": {},
                    "topEmojisOverall": [],
                    "topEmojisPerUser": [],
                    "longestStreak": {},
                    "longestStreakPerUser": [],
                    "dailyMessages": [],
                    "hourlyData": [],
                    "participantData": [],
                    "mostActiveDay": "Unknown",
                    "peakHour": "Unknown"
                }

            # ========== CREATE RAG SESSION ==========
            session_id = None
            expires_in_seconds = None
            
            if enable_rag:
                try:
                    session_id = create_rag_session(df)
                    expires_in_seconds = RAG_CACHE_TTL
                except Exception as rag_error:
                    # Don't fail the whole request, just skip RAG
                    pass

            # Build response with all data for frontend
            # Ensure all values are Python native types for JSON serialization
            stats = {
                # ========== BASIC STATS ==========
                "total_messages": int(total_messages),
                "messageCount": int(total_messages),
                "participants_count": int(participants_count),
                "top_user": str(top_user),
                "title": str(group_title),
                "emojiUsage": {k: int(v) for k, v in emoji_usage.items()},  # Convert counts to int
                
                # ========== RAG SESSION ==========
                "session_id": session_id,
                "expires_in_seconds": int(expires_in_seconds) if expires_in_seconds is not None else None,
                
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
            
            # Ensure all values are JSON serializable before returning
            stats_serializable = make_json_serializable(stats)
            return jsonify(stats_serializable)
            
    except Exception as e:
        error_msg = f"Error processing file: {str(e)}"
        error_traceback = traceback.format_exc()
        app.logger.error(error_msg)
        app.logger.error(error_traceback)
        # Always return JSON, even on error
        try:
            error_response = {
                "error": error_msg,
                "details": error_traceback if app.debug else "Check server logs for details"
            }
            # Ensure error response is serializable
            error_response_serializable = make_json_serializable(error_response)
            return jsonify(error_response_serializable), 500
        except Exception as json_error:
            # Fallback if jsonify fails - return plain text JSON string
            app.logger.error(f"Failed to return JSON error: {json_error}")
            app.logger.error(traceback.format_exc())
            from flask import Response
            return Response(
                '{"error": "Internal server error occurred"}',
                mimetype='application/json',
                status=500
            )


# WSGI application entry point for gunicorn
# Wrap the app to ensure JSON error responses even when gunicorn handles errors
class JSONErrorHandler:
    """WSGI middleware to ensure JSON error responses."""
    
    def __init__(self, app):
        self.app = app
    
    def __call__(self, environ, start_response):
        # Wrap start_response to catch errors
        def custom_start_response(status, headers, exc_info=None):
            # Ensure Content-Type is JSON for errors
            if status.startswith('500') or status.startswith('4'):
                # Remove any existing Content-Type
                headers = [(h, v) for h, v in headers if h.lower() != 'content-type']
                headers.append(('Content-Type', 'application/json'))
            return start_response(status, headers, exc_info)
        
        try:
            return self.app(environ, custom_start_response)
        except Exception as e:
            # Catch any unhandled exceptions and return JSON
            error_msg = str(e)
            error_traceback = traceback.format_exc()
            
            # Try to log, but don't fail if logger doesn't work
            try:
                import logging
                logging.error(f"Unhandled WSGI error: {error_msg}")
                logging.error(error_traceback)
            except:
                pass
            
            # Return JSON error response - always works
            try:
                error_response = {
                    "error": error_msg,
                    "details": error_traceback if app.debug else "Check server logs for details"
                }
                error_response_serializable = make_json_serializable(error_response)
                error_json = json.dumps(error_response_serializable)
            except Exception as json_err:
                # Fallback if serialization fails
                error_json = json.dumps({
                    "error": str(error_msg)[:200],  # Limit length
                    "details": "Error serializing error response"
                })
            
            status = '500 Internal Server Error'
            headers = [
                ('Content-Type', 'application/json'),
                ('Access-Control-Allow-Origin', '*'),
            ]
            start_response(status, headers)
            return [error_json.encode('utf-8')]

# Create WSGI application with error handler
# This MUST be used by gunicorn: analytics_service:application
application = JSONErrorHandler(app)

# Also export app for backward compatibility, but application is preferred
__all__ = ['app', 'application']

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5050,
        debug=True
    )
