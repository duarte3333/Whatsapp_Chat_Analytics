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
        self._emotion_detector = None  # Lazy initialization
    
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
            df['week'] = df['datetime'].dt.to_period('W').astype(str)
        return df
    
    def parse_to_csv(self, txt_file_path: str) -> pd.DataFrame:
        """Parse WhatsApp chat file to CSV."""
        messages = self._process_file(txt_file_path)
        
        if not messages:
            print("No messages found in file")
            return pd.DataFrame()
        
        # Set group name from first message's sender
        group_name = messages[0]['sender']
        self.set_group_name(group_name)
        print(f"Group name: {self.group_name}")
        
        # Filter out messages from the group name (group messages, not participant messages)
        #filtered_messages = [msg for msg in messages if msg['sender'] != group_name]
        # filtered_count = len(messages) - len(filtered_messages)
        filtered_messages = messages
        
        # if filtered_count > 0:
        #     print(f"Filtered out {filtered_count} messages from group '{group_name}'")
        
        df = self.create_dataframe(filtered_messages)        
        return df

    def _process_file(self, file_path: str) -> List[Dict[str, Any]]:
        """Process the WhatsApp chat file and return list of message dictionaries."""
        messages, current_message = [], None
        mapping = self.sender_mapper.sender_mapping
        
        print(f"Reading {file_path}...")
        # Read file in chunks for better performance
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
            line_num = 0
            for line in file:
                line_num += 1
                stripped = line.strip()
                if not stripped:
                    continue
                
                # Check if line might be a message (starts with [ for iOS or DD/MM for Android)
                is_potential_message = stripped.startswith('[') or (len(stripped) > 5 and stripped[2] == '/' and stripped[5] == '/')
                
                if is_potential_message:
                    cleaned_line = clean_unicode_controls(stripped)
                    new_message = parse_message_line(cleaned_line, line_num, sender_mapping=mapping)
                    
                    
                    if new_message:
                        if current_message:
                            messages.append(current_message)
                        current_message = new_message
                    elif current_message:
                        # Check for embedded message in continuation
                        if ']' in cleaned_line and ':' in cleaned_line:
                            if embedded := parse_message_line(cleaned_line, line_num, sender_mapping=mapping):
                                if current_message:
                                    messages.append(current_message)
                                current_message = embedded
                                continue
                        current_message['message'] += '\n' + cleaned_line
                elif current_message:
                    # Continuation line - check for embedded messages first
                    cleaned_stripped = clean_unicode_controls(stripped)
                    
                    # Check if this continuation line contains an embedded message pattern
                    if cleaned_stripped.startswith('[') and ']' in cleaned_stripped and ':' in cleaned_stripped:
                        # Try to parse as embedded message
                        if embedded := parse_message_line(cleaned_stripped, line_num, sender_mapping=mapping):
                            if current_message:
                                messages.append(current_message)
                            current_message = embedded
                            continue
                    
                    # Regular continuation - append to current message
                    current_message['message'] += '\n' + cleaned_stripped
            
            if current_message:
                messages.append(current_message)
        
        print(f"✓ Parsed {len(messages)} messages")
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

