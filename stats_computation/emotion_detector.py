"""Emotion detection using multilingual emotion models."""

from typing import List, Tuple
import pandas as pd


class EmotionDetector:
    """Emotion detection using multilingual emotion model."""
    #AnasAlokla/multilingual_go_emotions
    #MilaNLProc/xlm-emo-t
    def __init__(self, model_name: str = "tabularisai/multilingual-sentiment-analysis", batch_size: int = 256):
        """Initialize emotion detector."""
        self.model_name = model_name
        self.classifier = None
        self.model = None  # Direct model access for probabilities
        self.tokenizer = None
        self.max_length = 512  # Default max length
        self.batch_size = batch_size
        self._model_loaded = False
        # Continuous sentiment mapping: [Very Negative, Negative, Neutral, Positive, Very Positive]
        self.sentiment_scores = [-1.0, -0.5, 0.0, 0.5, 1.0]
        self.sentiment_labels = ["Very Negative", "Negative", "Neutral", "Positive", "Very Positive"]
    
    def _load_model(self):
        """Load the emotion detection model (lazy loading)."""
        if self._model_loaded:
            return
        
        try:
            from transformers import pipeline, logging, AutoTokenizer, AutoModelForSequenceClassification
            import torch
            logging.set_verbosity_error()
            
            # Use GPU if available, otherwise CPU
            device = 0 if torch.cuda.is_available() else -1
            device_str = f"cuda:{device}" if device >= 0 else "cpu"
            
            # Load tokenizer
            self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
            self.max_length = self.tokenizer.model_max_length if hasattr(self.tokenizer, 'model_max_length') else 512
            
            # Load model directly for getting probabilities
            self.model = AutoModelForSequenceClassification.from_pretrained(self.model_name)
            self.model.to(device_str)
            self.model.eval()
            
            # Get actual label mapping from model config
            if hasattr(self.model.config, 'id2label') and self.model.config.id2label:
                # Use model's actual labels
                id2label = self.model.config.id2label
                num_classes = len(id2label)
                self.sentiment_labels = [id2label[i] for i in range(num_classes)]
                print(f"Model labels detected: {self.sentiment_labels}")
            else:
                # Fallback to assumed labels
                self.sentiment_labels = ["Very Negative", "Negative", "Neutral", "Positive", "Very Positive"]
                print(f"Using default labels: {self.sentiment_labels}")
            
            # Map labels to scores (assuming order: Very Negative, Negative, Neutral, Positive, Very Positive)
            # If model has different labels, we need to map them correctly
            label_to_score = {}
            for i, label in enumerate(self.sentiment_labels):
                label_lower = label.lower()
                if 'very negative' in label_lower or 'very_negative' in label_lower:
                    label_to_score[i] = -1.0
                elif 'negative' in label_lower and 'very' not in label_lower:
                    label_to_score[i] = -0.5
                elif 'neutral' in label_lower:
                    label_to_score[i] = 0.0
                elif 'positive' in label_lower and 'very' not in label_lower:
                    label_to_score[i] = 0.5
                elif 'very positive' in label_lower or 'very_positive' in label_lower:
                    label_to_score[i] = 1.0
                else:
                    # Default mapping if we can't determine
                    label_to_score[i] = (i - 2) * 0.5  # Linear mapping: -1, -0.5, 0, 0.5, 1
            
            self.sentiment_scores = [label_to_score[i] for i in range(len(self.sentiment_labels))]
            print(f"Score mapping: {dict(zip(self.sentiment_labels, self.sentiment_scores))}")
            
            # Also keep pipeline for compatibility (though we'll use model directly)
            self.classifier = pipeline(
                "text-classification", 
                model=self.model_name,
                device=device,
                batch_size=self.batch_size
            )
            
            self._model_loaded = True
            device_name = "GPU" if device >= 0 else "CPU"
            print(f"Loaded emotion detection model: {self.model_name} on {device_name}")
        except ImportError:
            print("⚠️  transformers not installed. Install with: pip install transformers torch")
        except Exception as e:
            print(f"⚠️  Failed to load emotion model: {e}")
    
    def _normalize_sentiment_score(self, label: str, score: float) -> float:
        """Normalize sentiment score to a scale where positive is positive and negative is negative.
        
        Args:
            label: Sentiment label (e.g., 'positive', 'negative', 'neutral')
            score: Confidence score from model
            
        Returns:
            Normalized score (-1.0 to 1.0)
        """
        label_lower = label.lower()
        if 'positive' in label_lower or 'pos' in label_lower:
            return score  # Positive sentiment: keep score as positive
        elif 'negative' in label_lower or 'neg' in label_lower:
            return -score  # Negative sentiment: make score negative
        else:
            return 0.0  # Neutral: zero score
    
    def get_emotions_by_week(self, df: pd.DataFrame) -> pd.DataFrame:
        """Get sentiment per week by aggregating all messages in each week and predicting on the combined text.
        
        Adds two columns:
        - week_sentiment_score: Normalized sentiment score for the week (-1.0 to 1.0)
        - week_sentiment: Sentiment label for the week
        """
        self._load_model()  # Lazy load model only when needed
        
        if df.empty or 'datetime' not in df.columns or 'message' not in df.columns:
            return df
        
        # Ensure datetime column is datetime type
        df['datetime'] = pd.to_datetime(df['datetime'], errors='coerce')
        
        # Initialize week sentiment columns
        df['week_sentiment_score'] = 0.0
        df['week_sentiment'] = "neutral"
        
        unique_weeks = sorted(df['week'].unique())
        total_weeks = len(unique_weeks)
        print(f"Processing {total_weeks} weeks...")
        
        # Process each week: aggregate messages and predict sentiment
        for idx, week in enumerate(unique_weeks, 1):
            week_mask = df['week'] == week
            week_messages = df.loc[week_mask, 'message'].tolist()
            
            if not week_messages:
                continue
            
            # Aggregate all messages for this week into one text
            aggregated_text = ' '.join([msg.strip() for msg in week_messages if msg and msg.strip()])
            
            if not aggregated_text:
                continue
            
            # Truncate text to fit within model's token limit
            if self.tokenizer:
                # Tokenize and truncate properly
                tokens = self.tokenizer.encode(aggregated_text, max_length=self.max_length, truncation=True, return_tensors=None)
                truncated_text = self.tokenizer.decode(tokens, skip_special_tokens=True)
            else:
                # Fallback: simple character-based truncation (less accurate but works)
                # Approximate: 1 token ≈ 4 characters, so max_length * 4
                max_chars = self.max_length * 4
                truncated_text = aggregated_text[:max_chars] if len(aggregated_text) > max_chars else aggregated_text
            
            print(f"Processing week {idx}/{total_weeks} ({week}): {len(week_messages)} messages", end='\r')
            
            try:
                # Import torch here (it's already imported in _load_model, but need it here too)
                import torch
                
                # Use model directly to get probabilities for continuous mapping
                # Tokenize input
                inputs = self.tokenizer(truncated_text, return_tensors="pt", truncation=True, padding=True, max_length=self.max_length)
                
                # Move inputs to same device as model
                device = next(self.model.parameters()).device
                inputs = {k: v.to(device) for k, v in inputs.items()}
                
                # Get model output
                with torch.no_grad():
                    outputs = self.model(**inputs)
                
                # Get probabilities for all 5 classes
                probabilities = torch.nn.functional.softmax(outputs.logits, dim=-1)[0]
                
                # CONTINUOUS MAPPING: Calculate weighted average using all class probabilities
                # score = (prob_very_neg × -1.0) + (prob_neg × -0.5) + (prob_neutral × 0.0) + 
                #         (prob_pos × 0.5) + (prob_very_pos × 1.0)
                # This gives a continuous score from -1.0 to 1.0 based on confidence in each class
                continuous_score = sum(prob.item() * score for prob, score in zip(probabilities, self.sentiment_scores))
                
                # Get the label of the most likely class (for display)
                predicted_class = torch.argmax(probabilities).item()
                sentiment_label = self.sentiment_labels[predicted_class].lower()
                
                # Normalize label to simple categories
                if 'positive' in sentiment_label:
                    sentiment_label = 'positive'
                elif 'negative' in sentiment_label:
                    sentiment_label = 'negative'
                else:
                    sentiment_label = 'neutral'
                
                # Assign to all messages in this week (including media messages)
                full_week_mask = df['week'] == week
                df.loc[full_week_mask, 'week_sentiment_score'] = continuous_score
                df.loc[full_week_mask, 'week_sentiment'] = sentiment_label
                    
            except Exception as e:
                print(f"\n⚠️  Error processing week {week}: {e}")
                continue
        
        print(f"\n✓ Completed processing {total_weeks} weeks")
        return df