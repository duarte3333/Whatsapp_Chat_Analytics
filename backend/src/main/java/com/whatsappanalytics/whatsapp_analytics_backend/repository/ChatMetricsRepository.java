package com.whatsappanalytics.whatsapp_analytics_backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;

@Repository
public interface ChatMetricsRepository extends JpaRepository<ChatMetrics, Long> {
    List<ChatMetrics> findByGroupChatId(Long groupChatId);
    ChatMetrics findByIdAndGroupChatId(Long id, Long groupChatId);
}
