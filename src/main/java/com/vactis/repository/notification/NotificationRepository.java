package com.vactis.repository.notification;

import com.vactis.model.auth.Users;
import com.vactis.model.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(Users user);
    long countByUserAndLuFalse(Users user);
    List<Notification> findByUserAndLuFalse(Users user);
    void deleteByType(String type);
}
