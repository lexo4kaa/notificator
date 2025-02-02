package com.example.notificator.repository;

import com.example.notificator.model.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
    User getByChatId(Long id);
}
