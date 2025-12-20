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

    // JSON columns for legacy chart data
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

    // JSON columns for new chart data
    @Column(name = "messages_per_user", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String messagesPerUserJson;

    @Column(name = "weekly_volume", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String weeklyVolumeJson;

    @Column(name = "heatmap_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String heatmapDataJson;

    @Column(name = "message_type_breakdown", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String messageTypeBreakdownJson;

    @Column(name = "date_range", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dateRangeJson;

    // Emoji stats
    @Column(name = "top_emojis_overall", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String topEmojisOverallJson;

    @Column(name = "top_emojis_per_user", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String topEmojisPerUserJson;

    // Streak stats
    @Column(name = "longest_streak", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String longestStreakJson;

    @Column(name = "longest_streak_per_user", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String longestStreakPerUserJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
