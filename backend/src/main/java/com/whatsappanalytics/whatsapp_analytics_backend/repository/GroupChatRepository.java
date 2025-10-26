package com.whatsappanalytics.whatsapp_analytics_backend.repository;

import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {

    List<GroupChat> findByUserId_Id(Long userId);
    GroupChat findByIdAndUserId_Id(Long groupId, Long userId);

}
