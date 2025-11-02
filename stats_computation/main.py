#!/usr/bin/env python3
"""
Main entry point for WhatsApp chat parser.
"""

from parser import WhatsAppChatParser


if __name__ == "__main__":
    txt_file_path = "/home/duarte33/Documents/WhatsApp Chat - Drenas Espanholas/_chat_old.txt"
    
    # Parse chat
    parser = WhatsAppChatParser()
    df = parser.parse_to_csv(txt_file_path)

    # Setup mappings
    parser.setup_sender_mappings(df)

    #Francisco Vieira CPH is Kiks
    #parser.sender_mapper.sender_mapping['Francisco Vieira CPH'] = 'Kiks'
    
    # Apply mappings and save
    if parser.sender_mapping:
        df_mapped = parser.apply_mappings_to_dataframe(df.copy())
        df_mapped.to_csv("chat_parsed.csv", index=False, encoding='utf-8')
        parser.save_group_info(df_mapped, "group_info.csv")
    else:
        print("No mappings created - saving original data")
        df.to_csv("chat_parsed.csv", index=False, encoding='utf-8')
        parser.save_group_info(df, "group_info.csv")
    
    if parser.sender_mapping:
        print(f"\nFinal senders: {sorted(df_mapped['sender'].unique())}")
    else:
        print(f"\nFinal senders: {sorted(df['sender'].unique())}")
    print(f"Group: {parser.group_name}")

