package com.campus.lostfound.service;

import com.campus.lostfound.dto.CreateFlagRequest;
import com.campus.lostfound.dto.FlagView;
import com.campus.lostfound.entity.FlagStatus;
import com.campus.lostfound.entity.FlagTargetType;
import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportFlag;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.MessageRepository;
import com.campus.lostfound.repository.ReportFlagRepository;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.support.Texts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户侧举报业务：举报启事或会话消息。
 * 只读装配在事务内完成（open-in-view 已关闭）；处置动作由 AdminService 负责。
 */
@Service
public class FlagService {

    private static final int SUMMARY_MAX = 20;

    private final ReportFlagRepository reportFlagRepository;
    private final ReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public FlagService(ReportFlagRepository reportFlagRepository,
                       ReportRepository reportRepository,
                       MessageRepository messageRepository,
                       UserRepository userRepository) {
        this.reportFlagRepository = reportFlagRepository;
        this.reportRepository = reportRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /**
     * 举报启事：启事不存在 → 1002。
     * 若该启事当前为 OPEN 或 CLAIMING，则置为 PENDING_REVIEW，使其进入管理员复核队列；
     * 终态启事不因举报复活。
     */
    @Transactional
    public FlagView createReportFlag(Long userId, Long reportId, CreateFlagRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> BusinessException.notFound("启事不存在"));

        ReportFlag saved = reportFlagRepository.save(
                newFlag(userId, FlagTargetType.REPORT, reportId, request));

        if (report.getStatus() == ReportStatus.OPEN || report.getStatus() == ReportStatus.CLAIMING) {
            report.setStatus(ReportStatus.PENDING_REVIEW);
            reportRepository.save(report);
        }
        return toView(saved);
    }

    /** 举报会话消息：消息不存在 → 1002。 */
    @Transactional
    public FlagView createMessageFlag(Long userId, Long messageId, CreateFlagRequest request) {
        messageRepository.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("消息不存在"));

        ReportFlag saved = reportFlagRepository.save(
                newFlag(userId, FlagTargetType.MESSAGE, messageId, request));
        return toView(saved);
    }

    private ReportFlag newFlag(Long userId, FlagTargetType targetType, Long targetId,
                               CreateFlagRequest request) {
        ReportFlag flag = new ReportFlag();
        flag.setReporterId(userId);
        flag.setTargetType(targetType);
        flag.setTargetId(targetId);
        flag.setReason(request.reason());
        flag.setDetail(Texts.trimToNull(request.detail()));
        flag.setStatus(FlagStatus.PENDING);
        return flag;
    }

    private FlagView toView(ReportFlag flag) {
        String reporterNickname = userRepository.findById(flag.getReporterId())
                .map(User::getNickname)
                .orElse(null);
        return FlagView.from(flag, reporterNickname, summarize(flag));
    }

    /** 被举报内容摘要：启事取标题，消息取正文前若干字。 */
    private String summarize(ReportFlag flag) {
        if (flag.getTargetType() == FlagTargetType.REPORT) {
            return reportRepository.findById(flag.getTargetId())
                    .map(Report::getTitle)
                    .orElse(null);
        }
        return messageRepository.findById(flag.getTargetId())
                .map(message -> abbreviate(message.getBody()))
                .orElse(null);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= SUMMARY_MAX ? body : body.substring(0, SUMMARY_MAX) + "…";
    }
}
