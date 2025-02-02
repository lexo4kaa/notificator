package com.example.notificator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;
import java.util.List;

@Entity(name = "users")
@Getter
@Setter
@ToString
public class User {

    @Id
    private Long chatId;
    private String username;
    private Timestamp registeredAt;

    @OneToMany
    @JoinColumn(name = "userId")
    private List<Notification> notifications;

}
