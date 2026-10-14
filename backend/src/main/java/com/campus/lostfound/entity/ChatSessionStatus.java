package com.campus.lostfound.entity;

/**
 * 站内会话状态机。
 * OPEN（进行中）-> CLOSED（已关闭：交还完成或认领申请关闭）。
 */
public enum ChatSessionStatus {
    OPEN,
    CLOSED
}
