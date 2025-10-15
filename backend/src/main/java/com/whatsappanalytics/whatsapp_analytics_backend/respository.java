package com.whatsappanalytics.whatsapp_analytics_backend;



@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    
    Optional<User> findByEmail(String email)

    Optional<User> findByVerificationCode(String verificationCode)
}