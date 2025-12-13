# test_microservice.py (create this in python_microservice directory)
import requests
import os

# Test the /analyze endpoint
url = "https://localhost:5050/analyze"
# Use Linux path format (forward slashes) or raw string
# Option 1: Use Linux path (if file is in WSL filesystem)
zip_file_path = "/home/duarte33/Documents/WhatsApp Chat - Drenas Espanholas.zip"

# Option 2: If you need to use Windows path from WSL, use raw string:
# zip_file_path = r"\\wsl.localhost\Ubuntu-24.04\home\duarte33\Documents\WhatsApp Chat - Drenas Espanholas.zip"

# Option 3: Or escape the backslashes:
# zip_file_path = "\\\\wsl.localhost\\Ubuntu-24.04\\home\\duarte33\\Documents\\WhatsApp Chat - Drenas Espanholas.zip"

# Check if file exists
if not os.path.exists(zip_file_path):
    print(f"Error: File not found at {zip_file_path}")
    print("Please update zip_file_path to point to your actual ZIP file")
    exit(1)

print(f"Testing with file: {zip_file_path}")

# Disable SSL verification for local testing
try:
with open(zip_file_path, 'rb') as f:
    files = {'file': (os.path.basename(zip_file_path), f, 'application/zip')}
    response = requests.post(url, files=files, verify=False)

print(f"Status Code: {response.status_code}")
print(f"Response: {response.json()}")
except requests.exceptions.SSLError as e:
    print(f"SSL Error: {e}")
    print("Trying without SSL verification...")
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()