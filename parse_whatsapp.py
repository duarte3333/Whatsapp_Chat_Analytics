#!/usr/bin/env python3
"""
Simple WhatsApp chat parser that converts .txt to CSV
"""

import re
import pandas as pd
from datetime import datetime
import unicodedata
from typing import Optional, List, Dict, Any


class WhatsAppChatParser:
    """Parser for WhatsApp chat export files."""
    
    # WhatsApp message regex pattern: [DD/MM/YY, HH:MM:SS] Sender: Message
    MESSAGE_PATTERN = re.compile(r'^\[(\d{2}/\d{2}/\d{2}), (\d{2}:\d{2}:\d{2})\] ([^:]+): (.+)$')
    
    # Unicode control characters commonly found in WhatsApp exports
    UNICODE_CONTROL_CHARS = {
        '\u200E',  # Left-to-Right Mark
        '\u200F',  # Right-to-Left Mark
        '\u2068',  # First Strong Isolate
        '\u2069',  # Pop Directional Isolate
        '\u202A',  # Left-to-Right Embedding
        '\u202B',  # Right-to-Left Embedding
        '\u202C',  # Pop Directional Formatting
        '\u202D',  # Left-to-Right Override
        '\u202E',  # Right-to-Left Override
    }
    
    @staticmethod
    def clean_unicode_controls(text: str) -> str:
        """Remove Unicode control characters from text."""
        if not text:
            return text
        
        # Remove specific control characters
        for char in WhatsAppChatParser.UNICODE_CONTROL_CHARS:
            text = text.replace(char, '')
        
        # Remove any remaining control characters (categories 'Cc' and 'Cf')
        cleaned = ''.join(
            char for char in text 
            if unicodedata.category(char) not in ['Cc', 'Cf']
        )
        
        return cleaned.strip()
    
    @staticmethod
    def parse_timestamp(date_str: str, time_str: str) -> Optional[str]:
        """Parse WhatsApp date and time format to ISO timestamp."""
        try:
            datetime_str = f"{date_str} {time_str}"
            dt = datetime.strptime(datetime_str, "%d/%m/%y %H:%M:%S")
            return dt.strftime("%Y-%m-%d %H:%M:%S")
        except ValueError:
            return None


    def _parse_message_line(self, line: str, line_num: int) -> Optional[Dict[str, Any]]:
        """Parse a single line that starts a new message."""
        match = self.MESSAGE_PATTERN.match(line)
        if not match:
            return None
        
        date_str, time_str, sender, message = match.groups()
        
        return {
            'timestamp': self.parse_timestamp(date_str, time_str),
            'date': date_str,
            'time': time_str,
            'sender': self.clean_unicode_controls(sender.strip()),
            'message': self.clean_unicode_controls(message),
            'line_number': line_num
        }
    
    def _create_system_message(self, line: str, line_num: int) -> Dict[str, Any]:
        """Create a system message entry for malformed lines."""
        return {
            'timestamp': None,
            'date': None,
            'time': None,
            'sender': 'System',
            'message': self.clean_unicode_controls(line),
            'line_number': line_num
        }
    
    def _process_file(self, file_path: str) -> List[Dict[str, Any]]:
        """Process the WhatsApp chat file and return list of message dictionaries."""
        messages = []
        current_message = None
        
        print(f"📖 Reading {file_path}...")
        
        with open(file_path, 'r', encoding='utf-8') as file:
            for line_num, line in enumerate(file, 1):
                line = line.strip()
                
                if not line:
                    continue
                
                # Try to parse as new message
                new_message = self._parse_message_line(line, line_num)
                
                if new_message:
                    # Save previous message if exists
                    if current_message:
                        messages.append(current_message)
                    
                    current_message = new_message
                else:
                    # Continuation of previous message or system message
                    if current_message:
                        current_message['message'] += '\n' + self.clean_unicode_controls(line)
                    else:
                        messages.append(self._create_system_message(line, line_num))
            
            # Add the last message
            if current_message:
                messages.append(current_message)
        
        return messages
    
    def _create_dataframe(self, messages: List[Dict[str, Any]]) -> pd.DataFrame:
        """Convert messages list to pandas DataFrame with additional columns."""
        df = pd.DataFrame(messages)
        
        if not df.empty:
            # Add datetime column for easier analysis
            df['datetime'] = pd.to_datetime(df['timestamp'], errors='coerce')
            
            # Add additional useful columns
            df['hour'] = df['datetime'].dt.hour
            df['day_of_week'] = df['datetime'].dt.day_name()
            df['month'] = df['datetime'].dt.month_name()
            df['year'] = df['datetime'].dt.year
        
        return df
    
    def _print_statistics(self, df: pd.DataFrame, output_path: str) -> None:
        """Print parsing statistics."""
        print(f"✅ Parsed {len(df)} messages")
        print(f"📁 Saved to: {output_path}")
        
        if not df.empty:
            print(f"👥 {df['sender'].nunique()} unique senders")
            print(f"📅 Date range: {df['datetime'].min()} to {df['datetime'].max()}")
    
    def parse_to_csv(self, txt_file_path: str, csv_output_path: Optional[str] = None) -> pd.DataFrame:
        """
        Parse WhatsApp chat .txt file and save as CSV.
        
        Args:
            txt_file_path: Path to the WhatsApp .txt file
            csv_output_path: Path for output CSV file (optional)
        
        Returns:
            pd.DataFrame: Parsed chat data
        """
        # Set default output path if not provided
        if csv_output_path is None:
            csv_output_path = txt_file_path.replace('.txt', '_parsed.csv')
        
        # Process the file
        messages = self._process_file(txt_file_path)
        
        # Create DataFrame
        df = self._create_dataframe(messages)
        
        # Save to CSV
        df.to_csv(csv_output_path, index=False, encoding='utf-8')
        
        # Print statistics
        self._print_statistics(df, csv_output_path)
        
        return df

if __name__ == "__main__":
    # Example usage
    txt_file_path = "/home/duarte33/Documents/Whatsapp_Chat_Analytics/WhatsApp Chat - Drenas Espanholas/_chat.txt"
    
    # Parse and save to CSV
    parser = WhatsAppChatParser()
    df = parser.parse_to_csv(txt_file_path, None)
    
    # Show first few messages
    print("\n📋 Sample messages:")
    print(df[['datetime', 'sender', 'message']].head())
    
    # Show additional analysis columns
    print("\n📊 Available columns:")
    print(df.columns.tolist())
