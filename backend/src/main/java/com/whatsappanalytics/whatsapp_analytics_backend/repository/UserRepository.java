package com.whatsappanalytics.whatsapp_analytics_backend.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.whatsappanalytics.whatsapp_analytics_backend.model.User;



@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    
    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationCode(String verificationCode);
}