#!/usr/bin/env python3
"""
Simple WhatsApp chat parser that converts .txt to CSV
"""

import re
import pandas as pd
from datetime import datetime
import unicodedata
from typing import Optional, List, Dict, Any
from difflib import SequenceMatcher


class WhatsAppChatParser:
    """Parser for WhatsApp chat export files."""
    
    # WhatsApp message regex patterns
    # Pattern 1: Regular messages with colon [YY-MM-DD, HH:MM:SS] Sender: Message
    MESSAGE_PATTERN = re.compile(r'^\[(\d{2}/\d{2}/\d{2}), (\d{2}:\d{2}:\d{2})\] ([^:]+): (.+)$')
    
    # Pattern 2: Media messages - same structure as regular messages but with media content
    # We'll detect media messages by analyzing the content, not the structure
    MEDIA_PATTERN = re.compile(r'^\[(\d{2}/\d{2}/\d{2}), (\d{2}:\d{2}:\d{2})\] ([^:]+): (.+)$')
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
    
    def __init__(self):
        """Initialize the parser."""
        self.sender_mapping = {}
        self.group_name = "Unknown Group"
    
    def _has_consecutive_letters(self, name1: str, name2: str, min_length: int = 3) -> bool:
        """Check if two names have at least min_length consecutive letters in common."""
        name1_clean = name1.lower().replace(" ", "")
        name2_clean = name2.lower().replace(" ", "")
        
        # Check all possible substrings of min_length in name1
        for i in range(len(name1_clean) - min_length + 1):
            substring = name1_clean[i:i + min_length]
            if substring in name2_clean:
                return True
        
        return False
    
    def _find_automatic_matches(self, unsaved_contacts: List[str], regular_contacts: List[str]) -> Dict[str, str]:
        """Automatically find matches based on consecutive letters."""
        matches = {}
        
        for unsaved in unsaved_contacts:
            unsaved_clean = unsaved.lstrip("~\u202f\u00a0 ").strip()
            # Remove emojis for comparison
            unsaved_clean = self.remove_emojis(unsaved_clean)
            
            for regular in regular_contacts:
                # Remove emojis from regular contact for comparison
                regular_clean = self.remove_emojis(regular)
                
                if self._has_consecutive_letters(unsaved_clean, regular_clean, min_length=5):
                    matches[unsaved] = regular
                    print(f"✅ Auto-mapped '{unsaved}' → '{regular}' (shared letters)")
                    break
        
        return matches
    
    def setup_sender_mappings(self, df: pd.DataFrame) -> None:
        """Interactive setup for sender mappings."""
        unique_senders = sorted(df['sender'].unique())
        
        # Step 1: Find "Me" identifiers and show other senders
        me_identifiers = ["Eu", "Me", "Yo", "Ich", "Je", "Io", "Я", "أنا", "私", "我"]
        me_senders = [sender for sender in unique_senders if sender in me_identifiers]
        other_senders = [sender for sender in unique_senders if sender not in me_identifiers]
        
        if me_senders:
            print(f"\nFound 'Me' identifier: {me_senders[0]}")
            print(f"Found {len(other_senders)} other senders:")
            for i, sender in enumerate(other_senders, 1):
                print(f"  {i}. {sender}")
            
            choice = input("\nWhich number are you? (enter number, 0 to skip): ").strip()
            if choice.isdigit() and 1 <= int(choice) <= len(other_senders):
                selected_sender = other_senders[int(choice)-1]
                # Map the "Me" identifier to the selected sender
                self.sender_mapping[me_senders[0]] = selected_sender
                print(f"✅ Mapped '{me_senders[0]}' → '{selected_sender}'")
        
        # Step 2: Handle unsaved contacts with automatic matching
        unsaved_contacts = []
        regular_contacts = []
        
        for sender in unique_senders:
            # Check for various unsaved contact patterns
            if (sender.startswith("~") or 
                sender.startswith("~\u202f") or  # Unicode narrow no-break space
                sender.startswith("~\u00a0")):   # Non-breaking space
                unsaved_contacts.append(sender)
            else:
                regular_contacts.append(sender)
        
        if unsaved_contacts:
            print(f"\nFound unsaved contacts:")
            for sender in unsaved_contacts:
                print(f"  - {sender}")
            
            # Automatic matching based on consecutive letters
            auto_matches = self._find_automatic_matches(unsaved_contacts, regular_contacts)
            self.sender_mapping.update(auto_matches)
        
        # Step 3: Identify group name
        self.group_name = self._identify_group_name(unique_senders)
        
        # Step 4: Show final mappings
        if self.sender_mapping:
            print(f"\nFinal mappings:")
            for old_name, new_name in self.sender_mapping.items():
                print(f"  '{old_name}' → '{new_name}'")
    
    def _identify_group_name(self, unique_senders: List[str]) -> str:
        """Identify the group name from sender names."""
        # Look for potential group names (usually longer, descriptive names)
        potential_groups = []
        
        for sender in unique_senders:
            # Skip obvious personal names
            if sender in ["Eu", "Me", "Yo", "Ich", "Je", "Io"]:
                continue
            
            # Look for names that might be group names
            if (len(sender) > 10 or  # Long names
                " " in sender or     # Multi-word names
                "Chat" in sender or  # Contains "Chat"
                "Group" in sender):  # Contains "Group"
                potential_groups.append(sender)
        
        if potential_groups:
            print(f"\nPotential group names:")
            for i, group in enumerate(potential_groups, 1):
                print(f"  {i}. {group}")
            
            choice = input("Which one is the group name? (enter number, or 0 to skip): ").strip()
            if choice.isdigit() and 1 <= int(choice) <= len(potential_groups):
                return potential_groups[int(choice)-1]
        
        return "Unknown Group"
    
    def _apply_sender_mappings(self, sender: str) -> str:
        """Apply sender mappings if available."""
        return self.sender_mapping.get(sender, sender)
    
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
    def remove_emojis(text: str) -> str:
        """Remove emojis and other symbols from text."""
        if not text:
            return text
        
        # Remove emojis and symbols by keeping only letters, numbers, spaces, and basic punctuation
        # Categories to remove: So (Symbol, other), Sk (Symbol, modifier), Sm (Symbol, math), 
        # Sc (Symbol, currency), S (Symbol), Mn (Mark, nonspacing), Mc (Mark, spacing combining)
        # But preserve ~ character for unsaved contacts
        cleaned = ''.join(char for char in text if unicodedata.category(char) not in ['So', 'Sk', 'Sm', 'Sc', 'S', 'Mn', 'Mc'] or char == '~')
        
        # Clean up multiple spaces
        cleaned = ' '.join(cleaned.split())
        
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
    
    def _is_media_message(self, message: str) -> bool:
        """
        Detect media messages using language-agnostic heuristics.
        Media messages typically have these characteristics:
        1. Short length (usually 1-4 words)
        2. Contain media-related keywords
        3. Often contain words like 'omitted', 'not revealed', 'hidden', etc.
        4. May contain angle brackets or specific formatting
        """
        if not message:
            return False
        
        # Clean the message
        cleaned_message = self.clean_unicode_controls(message.strip())
        
        # Heuristic 1: Very short messages (1-4 words) with media indicators
        words = cleaned_message.split()
        if len(words) <= 4:
            # Check for common media-related patterns across languages
            message_lower = cleaned_message.lower()
            
            # Media type keywords (common across languages)
            media_types = [
                'image', 'photo', 'picture', 'imagem', 'foto', 'bild', 'image', 'photo',
                'audio', 'sound', 'voice', 'ficheiro', 'áudio', 'audio', 'ton',
                'video', 'vídeo', 'film', 'video', 'video',
                'document', 'file', 'documento', 'ficheiro', 'dokument',
                'sticker', 'sticker', 'autocollant', 'sticker',
                'contact', 'contacto', 'kontakt', 'contact',
                'location', 'localização', 'standort', 'location'
            ]
            
            # Status keywords (common across languages)
            status_words = [
                'omitted', 'hidden', 'not revealed', 'não revelado', 'não revelada',
                'omitted', 'versteckt', 'nicht angezeigt', 'caché', 'masqué',
                'omitted', 'nascosto', 'não mostrado', 'oculto', 'verborgen'
            ]
            
            # Check if message contains media type + status word combination
            has_media_type = any(media_type in message_lower for media_type in media_types)
            has_status_word = any(status in message_lower for status in status_words)
            
            # Check for angle bracket patterns (common in English exports)
            has_angle_brackets = '<' in message and '>' in message
            
            # Check for specific patterns like "Media omitted", "Image omitted", etc.
            has_omitted_pattern = 'omitted' in message_lower or 'não revelado' in message_lower
            
            if (has_media_type and has_status_word) or has_angle_brackets or has_omitted_pattern:
                return True
        
        # Heuristic 2: Check for very specific patterns that are almost always media
        message_lower = cleaned_message.lower()
        specific_patterns = [
            '<media omitted>', '<image omitted>', '<audio omitted>', '<video omitted>',
            'imagem não revelada', 'ficheiro de áudio não revelado', 'vídeo não revelado',
            'documento não revelado', 'contacto não revelado', 'localização não revelada'
        ]
        
        if any(pattern in message_lower for pattern in specific_patterns):
            return True
        
        return False


    def _parse_message_line(self, line: str, line_num: int) -> Optional[Dict[str, Any]]:
        """Parse a single line that starts a new message."""
        # Try to match the message pattern (both regular and media have same structure)
        match = self.MESSAGE_PATTERN.match(line)
        if match:
            date_str, time_str, sender, message = match.groups()
            # Use heuristic-based detection to determine if it's media
            is_media = self._is_media_message(message)
            if is_media:
                print(f"Media message detected: {sender} - {message}")
        else:
            return None
        
        # Clean sender name and apply mappings
        cleaned_sender = self.clean_unicode_controls(sender.strip())
        cleaned_sender = self.remove_emojis(cleaned_sender)
        mapped_sender = self._apply_sender_mappings(cleaned_sender)
        
        return {
            'timestamp': self.parse_timestamp(date_str, time_str),
            'date': date_str,
            'time': time_str,
            'sender': mapped_sender,
            'message': self.clean_unicode_controls(message),
            'media': 'yes' if is_media else 'no',
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
            'media': 'no',
            'line_number': line_num
        }
    
    def _process_file(self, file_path: str) -> List[Dict[str, Any]]:
        """Process the WhatsApp chat file and return list of message dictionaries."""
        messages = []
        current_message = None
        
        print(f"Reading {file_path}...")
        
        with open(file_path, 'r', encoding='utf-8') as file:
            for line_num, line in enumerate(file, 1):
                line = line.strip()
                
                if not line:
                    continue
                
                # Try to parse as new message (clean Unicode control characters first)
                cleaned_line = self.clean_unicode_controls(line)
                new_message = self._parse_message_line(cleaned_line, line_num)
                
                if new_message:
                    # Save previous message if exists
                    if current_message:
                        messages.append(current_message)
                    
                    current_message = new_message
                else:
                    # Check if this line starts with a timestamp pattern (embedded message)
                    if cleaned_line.startswith('[') and ']' in cleaned_line and ':' in cleaned_line:
                        # This might be an embedded message, try to parse it
                        embedded_message = self._parse_message_line(cleaned_line, line_num)
                        if embedded_message:
                            # Save previous message if exists
                            if current_message:
                                messages.append(current_message)
                            current_message = embedded_message
                        else:
                            # Continuation of previous message
                            if current_message:
                                current_message['message'] += '\n' + self.clean_unicode_controls(line)
                    else:
                        # Regular continuation of previous message
                        if current_message:
                            current_message['message'] += '\n' + self.clean_unicode_controls(line)
            
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
            
            # Filter out group name messages
            if hasattr(self, 'group_name') and self.group_name != "Unknown Group":
                df = df[df['sender'] != self.group_name]
        
        return df
    
    def apply_mappings_to_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        """Apply sender mappings to an existing DataFrame."""
        if self.sender_mapping:
            df['sender'] = df['sender'].map(lambda x: self._apply_sender_mappings(x))
        
        # Filter out group name messages
        if hasattr(self, 'group_name') and self.group_name != "Unknown Group":
            df = df[df['sender'] != self.group_name]
        
        return df
    
    def parse_to_csv(self, txt_file_path: str, csv_output_path: Optional[str] = None) -> pd.DataFrame:
        # Set default output path if not provided
        if csv_output_path is None:
            csv_output_path = txt_file_path.replace('.txt', '_parsed.csv')
        
        # Process the file
        messages = self._process_file(txt_file_path)
        
        # Create DataFrame
        df = self._create_dataframe(messages)
        
        # Save to CSV
        df.to_csv(csv_output_path, index=False, encoding='utf-8')

        return df
    
    def save_group_info(self, df: pd.DataFrame, output_path: str = "group_info.csv") -> None:
        """Save group information to CSV file."""
        # Get unique senders and their message counts
        sender_stats = df['sender'].value_counts().reset_index()
        sender_stats.columns = ['sender', 'message_count']
        
        # Add group name and other metadata
        group_info = {
            'group_name': [self.group_name],
            'total_messages': [len(df)],
            'unique_senders': [len(df['sender'].unique())],
            'date_range_start': [df['datetime'].min() if not df.empty else None],
            'date_range_end': [df['datetime'].max() if not df.empty else None],
            'sender_mappings': [str(self.sender_mapping) if self.sender_mapping else 'None']
        }
        
        # Create group metadata DataFrame
        group_metadata = pd.DataFrame(group_info)
        
        # Save group metadata
        group_metadata.to_csv(output_path, index=False, encoding='utf-8')
        
        # Save sender statistics to a separate file
        sender_stats_path = output_path.replace('.csv', '_senders.csv')
        sender_stats.to_csv(sender_stats_path, index=False, encoding='utf-8')
        
        print(f"Group info saved to: {output_path}")
        print(f"Sender stats saved to: {sender_stats_path}")

if __name__ == "__main__":
    txt_file_path = "/home/duarte33/Documents/WhatsApp Chat - Drenas Espanholas/_chat.txt"
    
    # Parse chat
    parser = WhatsAppChatParser()
    df = parser.parse_to_csv(txt_file_path, "chat_og.csv")
    
    # Setup mappings
    parser.setup_sender_mappings(df)

    #Francisco Vieira CPH is Kiks
    parser.sender_mapping['Francisco Vieira CPH'] = 'Kiks'
    
    # Apply mappings and save
    if parser.sender_mapping:
        df_mapped = parser.apply_mappings_to_dataframe(df.copy())
        df_mapped.to_csv("chat_parsed.csv", index=False, encoding='utf-8')
        
        # Save group information
        parser.save_group_info(df_mapped, "group_info.csv")
        
        print(f"\nFinal senders: {sorted(df_mapped['sender'].unique())}")
        print(f"Group: {parser.group_name}")
    else:
        print("No mappings created")
