package com.example.notificator.repository;

import com.example.notificator.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
    User getByChatId(Long id);

    @Query("select u from users u join fetch u.notifications where u.chatId = :id")
    User getByChatIdWithNotifications(Long id);

    @Query("select count(u) > 0 from users u join u.notifications n where u.chatId = :chatId and n.id = :notificationId")
    boolean hasNotification(Long chatId, Long notificationId);

}
