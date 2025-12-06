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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "participants_count")
    private int participantsCount;

    @Column(name = "most_active_day")
    private String mostActiveDay;

    @Column(name = "peak_hour")
    private String peakHour;

    // JSON columns for chart data
    @Column(name = "daily_messages", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dailyMessagesJson;

    @Column(name = "hourly_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String hourlyDataJson;

    @Column(name = "participant_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String participantDataJson;

    @Column(name = "emoji_usage", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String emojiUsageJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
