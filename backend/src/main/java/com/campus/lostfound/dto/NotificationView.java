package com.campus.lostfound.dto;

import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.entity.NotificationTargetType;
import com.campus.lostfound.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * 单条通知视图：含类型、关联对象（类型 + id）、已读标记与时间。
 */
public record NotificationView(
        Long id,
        NotificationType type,
        NotificationTargetType targetType,
        Long targetId,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationView from(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.isReadFlag(),
                notification.getCreatedAt()
        );
    }
}
