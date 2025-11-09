from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

model_name = "tabularisai/multilingual-sentiment-analysis"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(model_name)

def predict_sentiment(texts):
    inputs = tokenizer(texts, return_tensors="pt", truncation=True, padding=True, max_length=512)
    with torch.no_grad():
        outputs = model(**inputs)
    probabilities = torch.nn.functional.softmax(outputs.logits, dim=-1)
    sentiment_map = {0: "Very Negative", 1: "Negative", 2: "Neutral", 3: "Positive", 4: "Very Positive"}
    return [sentiment_map[p] for p in torch.argmax(probabilities, dim=-1).tolist()]

texts = [
    "Eu sou muito feliz com você",
    "Estou mesmo agoniado",
    "ahahahahahaha",
    "adoro a vida",
    "quero"
    ":))))",
    "Sinto-me ok",
    "Estou muito feliz",
    "Estou muito triste",
]

for text, sentiment in zip(texts, predict_sentiment(texts)):
    print(f"Text: {text}\nSentiment: {sentiment}\n")
