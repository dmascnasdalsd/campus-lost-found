package com.campus.lostfound.entity;

/**
 * 站内通知的业务类型，对应 notification.type（varchar(32)）。
 * 枚举名长度必须 ≤ 32。
 */
public enum NotificationType {
    /** 认领申请创建：通知启事发布者。 */
    CLAIM_CREATED,
    /** 认领申请被接受：通知申请人。 */
    CLAIM_ACCEPTED,
    /** 认领申请被驳回：通知申请人。 */
    CLAIM_REJECTED,
    /** 同启事其他待处理申请被自动关闭：通知对应申请人。 */
    CLAIM_CLOSED,
    /** 会话收到新消息：通知会话另一方。 */
    MESSAGE_RECEIVED,
    /** 启事被管理员下架：通知发布者。 */
    REPORT_TAKE_DOWN,
    /** 启事被管理员驳回：通知发布者。 */
    REPORT_REJECTED
}
