package com.whatsappanalytics.whatsapp_analytics_backend.repository;



@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    
    Optional<User> findByEmail(String email)

    Optional<User> findByVerificationCode(String verificationCode)
}