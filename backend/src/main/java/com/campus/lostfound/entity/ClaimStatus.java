package com.campus.lostfound.entity;

/**
 * 认领申请状态机。
 * PENDING（待处理）-> ACCEPTED（已接受）/ REJECTED（已驳回）；
 * PENDING -> WITHDRAWN（申请人主动撤回，可重新申请）；
 * ACCEPTED -> CLOSED（交还完成或会话关闭）。
 */
public enum ClaimStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    CLOSED
}
