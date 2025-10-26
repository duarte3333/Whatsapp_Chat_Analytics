from flask import Flask, request, jsonify
import zipfile
import tempfile
import os

app = Flask(__name__)

@app.route("/analyze", methods=["POST"])
def analyze():
    # Get uploaded file
    file = request.files.get("file")
    if file is None or not file.filename.endswith(".zip"):
        return jsonify({"error": "Invalid file"}), 400

    # Save temporarily
    with tempfile.TemporaryDirectory() as tmpdir:
        zip_path = os.path.join(tmpdir, file.filename)
        file.save(zip_path)

        # Extract ZIP (optional: read files for real processing)
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(tmpdir)

        # Simulate analytics (replace with real WhatsApp parsing later)
        stats = {
            "messageCount": 123,
            "topUser": "Alice",
            "emojiUsage": {"😂": 10, "❤️": 5}
        }

    return jsonify(stats)

if __name__ == "__main__":
    # Flask will listen on localhost:5000 by default
    app.run(host="0.0.0.0", port=5050, ssl_context=("certs/cert.pem", "certs/key.pem"))
