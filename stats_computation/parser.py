"""Main WhatsApp chat parser that converts .txt to CSV."""

import pandas as pd
from typing import Optional, List, Dict, Any
from text_utils import clean_unicode_controls
from message_parser import parse_message_line
from sender_mapper import SenderMapper
from emotion_detector import EmotionDetector


class WhatsAppChatParser:
    """Parser for WhatsApp chat export files."""
    
    def __init__(self):
        self.sender_mapper = SenderMapper()
        self.emotion_detector = EmotionDetector()
    
    def set_group_name(self, group_name: str) -> None:
        self.sender_mapper.group_name = group_name

    
    @property #getter for sender_mapping
    def sender_mapping(self) -> dict:
        return self.sender_mapper.sender_mapping
    
    @property #getter for group_name
    def group_name(self) -> str:
        return self.sender_mapper.group_name
    
    @group_name.setter #setter for group_name
    def group_name(self, value: str) -> None:
        self.sender_mapper.group_name = value
    
    def create_dataframe(self, messages: List[Dict[str, Any]]) -> pd.DataFrame:
        """Convert messages list to pandas DataFrame with additional columns."""
        df = pd.DataFrame(messages)
        if not df.empty:
            df['datetime'] = pd.to_datetime(df['timestamp'], errors='coerce')
            df['hour'] = df['datetime'].dt.hour
            df['day_of_week'] = df['datetime'].dt.day_name()
            df['month'] = df['datetime'].dt.month_name()
            df['year'] = df['datetime'].dt.year

            #df['emotion'] = df['message'].apply(self.get_emotion)
        return df
    
    def get_emotion(self, message: str) -> str:
        """Get the emotion of a message."""
        if self.emotion_detector:
            return self.emotion_detector.get_emotion(message)
        return "neutral"
    
    def parse_to_csv(self, txt_file_path: str) -> pd.DataFrame:
        """Parse WhatsApp chat file to CSV."""
        messages = self._process_file(txt_file_path)
        self.set_group_name(messages[0]['sender'])
        print(f"Group name: {self.group_name}")
        df = self.create_dataframe(messages)        
        return df

    def _process_file(self, file_path: str) -> List[Dict[str, Any]]:
        """Process the WhatsApp chat file and return list of message dictionaries."""
        messages, current_message = [], None
        mapping = self.sender_mapper.sender_mapping
        
        print(f"Reading {file_path}...")
        with open(file_path, 'r', encoding='utf-8') as file:
            for line_num, line in enumerate(file, 1):
                if not (line := line.strip()):
                    continue
                
                cleaned_line = clean_unicode_controls(line)
                new_message = parse_message_line(cleaned_line, line_num, sender_mapping=mapping)
                
                if new_message:
                    if current_message:
                        messages.append(current_message)
                    current_message = new_message
                elif current_message:
                    # Check for embedded message or continue previous
                    if cleaned_line.startswith('[') and ']' in cleaned_line and ':' in cleaned_line:
                        if embedded := parse_message_line(cleaned_line, line_num, sender_mapping=mapping):
                            if current_message:
                                messages.append(current_message)
                            current_message = embedded
                            continue
                    current_message['message'] += '\n' + cleaned_line
            
            if current_message:
                messages.append(current_message)
        
        return messages

    def setup_sender_mappings(self, df: pd.DataFrame) -> None:
        """Interactive setup for sender mappings."""
        self.sender_mapper.setup_mappings(sorted(df['sender'].unique()))

    def apply_mappings_to_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        """Apply sender mappings to an existing DataFrame."""
        return self.sender_mapper.apply_mappings_to_dataframe(df)
    
    def save_group_info(self, df: pd.DataFrame, output_path: str = "group_info.csv") -> None:
        """Save group information to CSV file."""
        sender_stats = df['sender'].value_counts().reset_index()
        sender_stats.columns = ['sender', 'message_count']
        
        group_info = {
            'group_name': [self.group_name],
            'total_messages': [len(df)],
            'unique_senders': [len(df['sender'].unique())],
            'date_range_start': [df['datetime'].min() if not df.empty else None],
            'date_range_end': [df['datetime'].max() if not df.empty else None],
            'sender_mappings': [str(self.sender_mapping) if self.sender_mapping else 'None']
        }
        
        pd.DataFrame(group_info).to_csv(output_path, index=False, encoding='utf-8')
        sender_stats_path = output_path.replace('.csv', '_senders.csv')
        sender_stats.to_csv(sender_stats_path, index=False, encoding='utf-8')
        
        print(f"Group info saved to: {output_path}")
        print(f"Sender stats saved to: {sender_stats_path}")

