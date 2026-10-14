package com.campus.lostfound.entity;

/**
 * 启事状态机。
 * OPEN -> CLAIMING -> RETURNED / HANDED_OVER
 * OPEN -> PENDING_REVIEW（被举报或管理员主动下架待复核）-> OPEN（复核通过）/ REJECTED（驳回）
 * OPEN -> OFFLINE（管理员下架）
 */
public enum ReportStatus {
    OPEN,
    PENDING_REVIEW,
    CLAIMING,
    RETURNED,
    HANDED_OVER,
    OFFLINE,
    REJECTED
}
