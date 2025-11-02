"""Emotion detection using multilingual emotion models."""


class EmotionDetector:
    """Emotion detection using multilingual emotion model."""
    #AnasAlokla/multilingual_go_emotions
    #MilaNLProc/xlm-emo-t
    def __init__(self, model_name: str = "tabularisai/multilingual-sentiment-analysis"):
        """Initialize emotion detector."""
        self.model_name = model_name
        self.classifier = None
        self._load_model()
    
    def _load_model(self):
        """Load the emotion detection model."""
        try:
            from transformers import pipeline, logging
            logging.set_verbosity_error()
            self.classifier = pipeline("text-classification", model=self.model_name)
            print(f"Loaded emotion detection model: {self.model_name}")
        except ImportError:
            print("⚠️  transformers not installed. Install with: pip install transformers torch")
        except Exception as e:
            print(f"⚠️  Failed to load emotion model: {e}")
    
    def get_emotion(self, text: str) -> str:
        """Get emotion from message text."""
        if not text or not text.strip() or not self.classifier:
            return "neutral"
        
        try:
            result = self.classifier(text)
            print(result)
            return result[0]['label'].lower() if isinstance(result, list) and result else "neutral"

        except Exception:
            return "neutral"

