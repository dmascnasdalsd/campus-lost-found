package com.campus.lostfound.dto;

import com.campus.lostfound.entity.FlagHandleResult;
import com.campus.lostfound.entity.FlagReason;
import com.campus.lostfound.entity.FlagStatus;
import com.campus.lostfound.entity.FlagTargetType;
import com.campus.lostfound.entity.ReportFlag;

import java.time.LocalDateTime;

/**
 * 举报视图：含被举报内容摘要（启事取标题、消息取正文前缀）、举报人与处置信息。
 * 昵称与摘要由 Service 装配后传入。
 */
public record FlagView(
        Long id,
        FlagTargetType targetType,
        Long targetId,
        String targetSummary,
        FlagReason reason,
        String detail,
        FlagStatus status,
        FlagHandleResult handleResult,
        Long reporterId,
        String reporterNickname,
        Long handledBy,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {

    public static FlagView from(ReportFlag flag, String reporterNickname, String targetSummary) {
        return new FlagView(
                flag.getId(),
                flag.getTargetType(),
                flag.getTargetId(),
                targetSummary,
                flag.getReason(),
                flag.getDetail(),
                flag.getStatus(),
                flag.getHandleResult(),
                flag.getReporterId(),
                reporterNickname,
                flag.getHandledBy(),
                flag.getHandledAt(),
                flag.getCreatedAt()
        );
    }
}
