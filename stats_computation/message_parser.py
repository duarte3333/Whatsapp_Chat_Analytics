"""Message parsing logic for WhatsApp chat exports."""

import re
from datetime import datetime
from typing import Optional, Dict, Any

from text_utils import clean_unicode_controls, remove_emojis

# Me identifiers to filter out
ME_IDENTIFIERS = {"Eu", "Me", "Yo", "Ich", "Je", "Io", "Я", "أنا", "私", "我"}

# Pattern supports both DD/MM/YYYY and DD/MM/YY formats
MESSAGE_PATTERN = re.compile(r'^\[(\d{2}/\d{2}/\d{2,4}), (\d{2}:\d{2}:\d{2})\] ([^:]+): (.+)$')

# Media detection patterns
MEDIA_TYPES = {'image', 'photo', 'picture', 'imagem', 'foto', 'bild', 'audio', 'sound', 'voice', 
               'ficheiro', 'áudio', 'ton', 'video', 'vídeo', 'film', 'document', 'file', 
               'documento', 'dokument', 'sticker', 'autocollant', 'contact', 'contacto', 
               'kontakt', 'location', 'localização', 'standort'}

STATUS_WORDS = {'omitted', 'hidden', 'not revealed', 'não revelado', 'não revelada', 
                'versteckt', 'nicht angezeigt', 'caché', 'masqué', 'nascosto', 
                'não mostrado', 'oculto', 'verborgen'}

SPECIFIC_PATTERNS = [
    '<media omitted>', '<image omitted>', '<audio omitted>', '<video omitted>',
    'imagem não revelada', 'ficheiro de áudio não revelado', 'vídeo não revelado',
    'documento não revelado', 'contacto não revelado', 'localização não revelada'
]


def parse_timestamp(date_str: str, time_str: str) -> Optional[str]:
    """Parse WhatsApp date and time format to ISO timestamp."""
    try:
        fmt = "%d/%m/%y %H:%M:%S" if len(date_str.split('/')[-1]) == 2 else "%d/%m/%Y %H:%M:%S"
        return datetime.strptime(f"{date_str} {time_str}", fmt).strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return None


def is_media_message(message: str) -> bool:
    """Detect media messages using language-agnostic heuristics."""
    if not message:
        return False
    
    cleaned = clean_unicode_controls(message.strip())
    msg_lower = cleaned.lower()
    
    # Heuristic 1: Short messages (1-4 words) with media indicators
    if len(cleaned.split()) <= 4:
        has_media = any(mt in msg_lower for mt in MEDIA_TYPES)
        has_status = any(sw in msg_lower for sw in STATUS_WORDS)
        if (has_media and has_status) or ('<' in message and '>' in message) or 'omitted' in msg_lower or 'não revelado' in msg_lower:
            return True
    
    # Heuristic 2: Very specific patterns
    return any(pattern in msg_lower for pattern in SPECIFIC_PATTERNS)


def parse_message_line(line: str, line_num: int, sender_mapping: dict = None) -> Optional[Dict[str, Any]]:
    """Parse a single line that starts a new message."""
    match = MESSAGE_PATTERN.match(line)
    if not match:
        return None
    
    date_str, time_str, sender, message = match.groups()
    
    cleaned_sender = remove_emojis(clean_unicode_controls(sender.strip()))
    
    # Filter out "Me" identifiers (Eu, Me, Yo, etc.)
    if cleaned_sender in ME_IDENTIFIERS:
        return None
    
    is_media = is_media_message(message)
    mapped_sender = sender_mapping.get(cleaned_sender, cleaned_sender) if sender_mapping else cleaned_sender
    
    return {
        'timestamp': parse_timestamp(date_str, time_str),
        'date': date_str,
        'time': time_str,
        'sender': mapped_sender,
        'message': clean_unicode_controls(message),
        'media': 'yes' if is_media else 'no',
        'line_number': line_num
    }

