package com.whatsappanalytics.whatsapp_analytics_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ShareLink;

import java.util.Optional;
import java.util.List;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByToken(String token);
    List<ShareLink> findByGroupId(Long groupId);
    List<ShareLink> findByUserId(Long userId);
    List<ShareLink> findByGroupIdAndUserId(Long groupId, Long userId);
}


