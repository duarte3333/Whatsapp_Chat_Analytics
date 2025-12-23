package com.whatsappanalytics.whatsapp_analytics_backend.model;


import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.GeneratedValue;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
@Table(name = "users")
@Getter
@Setter
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;

    @Column(name = "group_ids", nullable = false)
    private List<Integer> groupIds;

    private boolean enabled;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_expiration")
    private LocalDateTime verificationCodeExpiresAt;

    @Column(name = "credits", nullable = false)
    private Integer remainingCredits = 3;

    @Column(name = "has_lifetime", nullable = false)
    private Boolean hasLifetimeAccess = false;




    public User(String username, String email, String password){
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public User() {
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired(){ //TODO: implement logic
        return true;
    }

    @Override
    public boolean isAccountNonLocked(){ //TODO: implement logic
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired(){ //TODO: implement logic
        return true;
    }

    @Override
    public boolean isEnabled(){ 
        return enabled;
    }

    @JsonIgnore
    @Override
    public String getUsername() {
        return email;
    }

    @JsonGetter("username")
    public String getDisplayName() {
        return username;
    }
}