package com.campus.lostfound.dto;

import com.campus.lostfound.entity.ChatSession;
import com.campus.lostfound.entity.ChatSessionStatus;

import java.time.LocalDateTime;

/**
 * 会话视图：含关联启事摘要与对方昵称，供「我的会话」列表展示。
 * 对方昵称由 Service 装配传入（对方 = 发布者与申请人中不是我的那个）。
 */
public record ChatSessionView(
        Long id,
        Long reportId,
        ReportSummary report,
        String peerNickname,
        ChatSessionStatus status,
        LocalDateTime createdAt,
        LocalDateTime closedAt
) {

    public static ChatSessionView from(ChatSession session, ReportSummary report, String peerNickname) {
        return new ChatSessionView(
                session.getId(),
                session.getReportId(),
                report,
                peerNickname,
                session.getStatus(),
                session.getCreatedAt(),
                session.getClosedAt()
        );
    }
}
