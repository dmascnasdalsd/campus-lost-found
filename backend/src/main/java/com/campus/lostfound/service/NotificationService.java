package com.campus.lostfound.service;

import com.campus.lostfound.dto.NotificationListView;
import com.campus.lostfound.dto.NotificationView;
import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.entity.NotificationTargetType;
import com.campus.lostfound.entity.NotificationType;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.NotificationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内通知的业务编排。
 * 只依赖 NotificationRepository，不反向依赖投递方，避免循环依赖；
 * notify 通常被其他 service 的事务方法调用，参与同一事务。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 投递一条未读通知。recipientId 为空时直接返回（防御：避免外键失败）。
     */
    @Transactional
    public void notify(Long recipientId, NotificationType type,
                       NotificationTargetType targetType, Long targetId) {
        if (recipientId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setReadFlag(false);
        notificationRepository.save(notification);
    }

    /** 我的通知列表（时间倒序）与未读数。 */
    @Transactional(readOnly = true)
    public NotificationListView list(Long userId) {
        var items = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationView::from)
                .toList();
        int unreadCount = notificationRepository.countByRecipientIdAndReadFlagFalse(userId);
        return new NotificationListView(items, unreadCount);
    }

    /** 标记单条已读：不存在→1002；非本人→1004。 */
    @Transactional
    public NotificationView markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!notification.getRecipientId().equals(userId)) {
            throw BusinessException.forbidden("只能操作自己的通知");
        }
        notification.setReadFlag(true);
        return NotificationView.from(notificationRepository.save(notification));
    }

    /** 把我全部未读置为已读，返回本次标记数量。 */
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadByRecipientId(userId);
    }
}
