package com.campus.lostfound.dto;

import com.campus.lostfound.entity.Claim;
import com.campus.lostfound.entity.ClaimDirection;
import com.campus.lostfound.entity.ClaimStatus;

import java.time.LocalDateTime;

/**
 * 认领申请视图：含关联启事摘要与申请人昵称，供「我的认领」列表与提交结果展示。
 * 申请人昵称由 Service 装配传入（本表不直接关联 app_user 实体）。
 */
public record ClaimView(
        Long id,
        ClaimDirection direction,
        ClaimStatus status,
        String featureDesc,
        LocalDateTime expectTime,
        String expectPlace,
        String rejectReason,
        LocalDateTime handledAt,
        LocalDateTime createdAt,
        ReportSummary report,
        Long applicantId,
        String applicantNickname
) {

    public static ClaimView from(Claim claim, ReportSummary report, String applicantNickname) {
        return new ClaimView(
                claim.getId(),
                claim.getDirection(),
                claim.getStatus(),
                claim.getFeatureDesc(),
                claim.getExpectTime(),
                claim.getExpectPlace(),
                claim.getRejectReason(),
                claim.getHandledAt(),
                claim.getCreatedAt(),
                report,
                claim.getApplicantId(),
                applicantNickname
        );
    }
}
