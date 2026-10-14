package com.campus.lostfound.entity;

/**
 * 通知关联对象的类型，对应 notification.target_type（varchar(16)）。
 * 枚举名长度必须 ≤ 16。
 */
public enum NotificationTargetType {
    /** 关联启事。 */
    REPORT,
    /** 关联认领申请。 */
    CLAIM,
    /** 关联站内会话。 */
    SESSION
}
