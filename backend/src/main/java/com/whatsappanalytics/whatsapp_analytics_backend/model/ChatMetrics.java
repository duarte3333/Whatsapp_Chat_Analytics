package com.whatsappanalytics.whatsapp_analytics_backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ChatMetrics")
public class ChatMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_chat_id", nullable = false)
    private GroupChat groupChat;

    private int totalMessages;

    @Column(columnDefinition = "json")
    private String messagesPerSender;

    @Column(columnDefinition = "json")
    private String topWords;

    @Column(columnDefinition = "json")
    private String emojiCounts;

    @Column(columnDefinition = "json")
    private String sentimentScores;

    private LocalDateTime updatedAt;
}

