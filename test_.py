from transformers import pipeline

emotion_classifier = pipeline(
    "text-classification",
    model="AnasAlokla/multilingual_go_emotions",
    top_k=None  # returns all emotion scores
)

result = emotion_classifier("correu mesmo mal isto")
print(result[0][0]['label'])
