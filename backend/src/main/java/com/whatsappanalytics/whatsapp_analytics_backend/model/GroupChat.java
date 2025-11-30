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
@Table(name = "group_chat")
public class GroupChat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "participants_count", nullable = false)
    private int participantsCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
