package com.whatsappanalytics.whatsapp_analytics_backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(name = "chat_metrics")
public class ChatMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_chat_id", nullable = false)
    private long groupChatId;

    @Column(name = "total_messages", nullable = false)
    private int totalMessages;

    @Column(name = "top_user", nullable = false)
    private String topUser;

    @Column(name = "created_at", nullable =  false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
