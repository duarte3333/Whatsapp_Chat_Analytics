package com.whatsappanalytics.whatsapp_analytics_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;

@Repository
public interface ChatMetricsRepository extends JpaRepository<ChatMetrics, Long> {
    ChatMetrics findByGroupChatId(Long groupChatId);
}
