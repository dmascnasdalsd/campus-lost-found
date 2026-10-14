package com.campus.lostfound.repository;

import com.campus.lostfound.entity.Notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 我的通知列表，按创建时间倒序。 */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** 我的未读数。 */
    int countByRecipientIdAndReadFlagFalse(Long recipientId);

    /** 把我全部未读通知一次性置为已读，返回本次标记数量。 */
    @Modifying
    @Query("update Notification n set n.readFlag = true "
            + "where n.recipientId = :recipientId and n.readFlag = false")
    int markAllReadByRecipientId(@Param("recipientId") Long recipientId);
}
