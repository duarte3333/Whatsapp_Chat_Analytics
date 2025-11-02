"""Sender mapping and group identification logic."""

import pandas as pd
from typing import List, Dict
from text_utils import remove_emojis

def has_consecutive_letters(name1: str, name2: str, min_length: int = 3) -> bool:
    """Check if two names have at least min_length consecutive letters in common."""
    name1_clean = name1.lower().replace(" ", "")
    name2_clean = name2.lower().replace(" ", "")
    return any(name1_clean[i:i + min_length] in name2_clean 
               for i in range(len(name1_clean) - min_length + 1))


def find_automatic_matches(unsaved_contacts: List[str], regular_contacts: List[str]) -> Dict[str, str]:
    """Automatically find matches based on consecutive letters."""
    matches = {}
    for unsaved in unsaved_contacts:
        unsaved_clean = remove_emojis(unsaved.lstrip("~\u202f\u00a0 ").strip())
        for regular in regular_contacts:
            if has_consecutive_letters(unsaved_clean, remove_emojis(regular), min_length=5):
                matches[unsaved] = regular
                print(f"✅ Auto-mapped '{unsaved}' → '{regular}' (shared letters)")
                break
    return matches


class SenderMapper:
    """Handles sender mapping and group identification."""
    
    def __init__(self):
        self.sender_mapping = {}
        self.group_name = "Unknown Group"
    
    def setup_mappings(self, unique_senders: List[str]) -> None:
        """Interactive setup for sender mappings."""
        unique_senders = sorted(unique_senders)
        
        #Handle unsaved contacts
        unsaved = [s for s in unique_senders if s.startswith("~") or s.startswith("~\u202f") or s.startswith("~\u00a0")]
        regular = [s for s in unique_senders if s not in unsaved]
        
        if unsaved:
            print(f"\nFound unsaved contacts:")
            for sender in unsaved:
                print(f"  - {sender}")
            self.sender_mapping.update(find_automatic_matches(unsaved, regular))

        # Show final mappings
        if self.sender_mapping:
            print(f"\nFinal mappings:")
            for old_name, new_name in self.sender_mapping.items():
                print(f"  '{old_name}' → '{new_name}'")
    
    def apply_mapping(self, sender: str) -> str:
        """Apply sender mapping if available."""
        return self.sender_mapping.get(sender, sender)
    
    def apply_mappings_to_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        """Apply sender mappings to an existing DataFrame."""
        if self.sender_mapping:
            df['sender'] = df['sender'].map(lambda x: self.sender_mapping.get(x, x))
            # Check for unmapped senders that start with ~
            unmapped = [s for s in df['sender'].unique() if s.startswith("~")]
            if unmapped:
                print(f"\n⚠️  Warning: Found unmapped senders starting with '~': {unmapped}")
        return df

