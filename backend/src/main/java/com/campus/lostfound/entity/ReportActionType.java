package com.campus.lostfound.entity;

/**
 * 管理员内容治理动作类型。
 * APPROVE 复核通过 → OPEN；REJECT 驳回 → REJECTED；
 * TAKE_DOWN 下架 → OFFLINE；PIN / UNPIN 置顶与取消置顶。
 */
public enum ReportActionType {
    APPROVE,
    REJECT,
    TAKE_DOWN,
    PIN,
    UNPIN
}
