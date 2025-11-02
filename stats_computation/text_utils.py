"""
Text utilities for cleaning WhatsApp chat exports.
"""

import unicodedata


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


def clean_unicode_controls(text: str) -> str:
    """Remove Unicode control characters from text."""
    if not text:
        return text
    
    # Remove specific control characters
    for char in UNICODE_CONTROL_CHARS:
        text = text.replace(char, '')
    
    # Remove any remaining control characters (categories 'Cc' and 'Cf')
    cleaned = ''.join(
        char for char in text 
        if unicodedata.category(char) not in ['Cc', 'Cf']
    )
    
    return cleaned.strip()


def remove_emojis(text: str) -> str:
    """Remove emojis and other symbols from text."""
    if not text:
        return text
    
    # Remove emojis and symbols by keeping only letters, numbers, spaces, and basic punctuation
    # Categories to remove: So (Symbol, other), Sk (Symbol, modifier), Sm (Symbol, math), 
    # Sc (Symbol, currency), S (Symbol), Mn (Mark, nonspacing), Mc (Mark, spacing combining)
    # But preserve ~ character for unsaved contacts
    cleaned = ''.join(
        char for char in text 
        if unicodedata.category(char) not in ['So', 'Sk', 'Sm', 'Sc', 'S', 'Mn', 'Mc'] or char == '~'
    )
    
    # Clean up multiple spaces
    cleaned = ' '.join(cleaned.split())
    
    return cleaned.strip()

