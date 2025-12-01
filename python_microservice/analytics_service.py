from flask import Flask, request, jsonify
import zipfile
import tempfile
import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI

load_dotenv()

app = Flask(__name__)

@app.route("/analyze", methods=["POST"])
def analyze_zip():
    file = request.files.get("file")
    if file is None or not file.filename.endswith(".zip"):
        return jsonify({"error": "Invalid file"}), 400

    with tempfile.TemporaryDirectory() as tmpdir:
        zip_path = os.path.join(tmpdir, file.filename)
        file.save(zip_path)

        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(tmpdir)

        stats = {
            "messageCount": 123,
            "topUser": "Alice",
            "emojiUsage": {"😂": 10, "❤️": 5}
        }

    return jsonify(stats)

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
        ssl_context=("certs/cert.pem", "certs/key.pem")
    )
