package com.campus.lostfound.service;

import com.campus.lostfound.dto.AuditLogView;
import com.campus.lostfound.dto.FlagView;
import com.campus.lostfound.dto.PageResult;
import com.campus.lostfound.dto.PublisherSummary;
import com.campus.lostfound.dto.ReportDetail;
import com.campus.lostfound.entity.FlagHandleResult;
import com.campus.lostfound.entity.FlagStatus;
import com.campus.lostfound.entity.FlagTargetType;
import com.campus.lostfound.entity.Message;
import com.campus.lostfound.entity.NotificationTargetType;
import com.campus.lostfound.entity.NotificationType;
import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportAction;
import com.campus.lostfound.entity.ReportActionType;
import com.campus.lostfound.entity.ReportFlag;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.entity.UserStatus;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.MessageRepository;
import com.campus.lostfound.repository.ReportActionRepository;
import com.campus.lostfound.repository.ReportFlagRepository;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理员内容治理：复核队列、通过 / 驳回 / 下架 / 置顶，并记录审计。
 * 角色校验由 SecurityConfig 的 /api/admin/** 规则负责，Service 只管业务编排。
 */
@Service
public class AdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportActionRepository reportActionRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;

    public AdminService(ReportRepository reportRepository,
                        UserRepository userRepository,
                        ReportActionRepository reportActionRepository,
                        ReportFlagRepository reportFlagRepository,
                        MessageRepository messageRepository,
                        NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.reportActionRepository = reportActionRepository;
        this.reportFlagRepository = reportFlagRepository;
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
    }

    /**
     * 复核队列：status 为空时返回全部启事；为合法枚举时按状态过滤；非法值 → 1001。
     * 按 createdAt 倒序分页返回启事详情（含图片与描述）。
     */
    @Transactional(readOnly = true)
    public PageResult<ReportDetail> listReports(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(safePage(page), safeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Report> result;
        if (!StringUtils.hasText(status)) {
            result = reportRepository.findAll(pageable);
        } else {
            ReportStatus parsed = parseStatus(status);
            result = reportRepository.findByStatus(parsed, pageable);
        }
        return PageResult.of(result.map(this::toDetail));
    }

    /** 复核通过：恢复为 OPEN，清空驳回原因，记录审计。 */
    @Transactional
    public ReportDetail approve(Long adminId, Long reportId) {
        Report report = loadReport(reportId);
        report.setStatus(ReportStatus.OPEN);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        report.setRejectReason(null);
        record(adminId, ReportActionType.APPROVE, reportId, null);
        return toDetail(reportRepository.save(report));
    }

    /** 驳回：置为 REJECTED 并记录驳回原因（必填），记录审计。 */
    @Transactional
    public ReportDetail reject(Long adminId, Long reportId, String reason) {
        String normalized = requireReason(reason, "驳回原因");
        Report report = loadReport(reportId);
        report.setStatus(ReportStatus.REJECTED);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        report.setRejectReason(normalized);
        record(adminId, ReportActionType.REJECT, reportId, normalized);
        Report saved = reportRepository.save(report);
        // 通知发布者：其启事被驳回。
        notificationService.notify(saved.getPublisherId(), NotificationType.REPORT_REJECTED,
                NotificationTargetType.REPORT, reportId);
        return toDetail(saved);
    }

    /** 下架：置为 OFFLINE（原因必填），记录审计。 */
    @Transactional
    public ReportDetail takeDown(Long adminId, Long reportId, String reason) {
        String normalized = requireReason(reason, "下架原因");
        Report report = loadReport(reportId);
        report.setStatus(ReportStatus.OFFLINE);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        record(adminId, ReportActionType.TAKE_DOWN, reportId, normalized);
        Report saved = reportRepository.save(report);
        // 通知发布者：其启事被下架。
        notificationService.notify(saved.getPublisherId(), NotificationType.REPORT_TAKE_DOWN,
                NotificationTargetType.REPORT, reportId);
        return toDetail(saved);
    }

    /** 置顶：pinned=true，记录审计。 */
    @Transactional
    public ReportDetail pin(Long adminId, Long reportId) {
        Report report = loadReport(reportId);
        report.setPinned(true);
        record(adminId, ReportActionType.PIN, reportId, null);
        return toDetail(reportRepository.save(report));
    }

    /** 取消置顶：pinned=false，记录审计。 */
    @Transactional
    public ReportDetail unpin(Long adminId, Long reportId) {
        Report report = loadReport(reportId);
        report.setPinned(false);
        record(adminId, ReportActionType.UNPIN, reportId, null);
        return toDetail(reportRepository.save(report));
    }

    /** 审计日志：按 createdAt 倒序分页，装配操作人昵称与启事标题。 */
    @Transactional(readOnly = true)
    public PageResult<AuditLogView> auditLogs(int page, int size) {
        PageRequest pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<ReportAction> result = reportActionRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResult.of(result.map(this::toView));
    }

    /**
     * 举报列表：status 为空时返回全部；为合法 FlagStatus 时按状态过滤；非法值 → 1001。
     * 按举报时间倒序分页，装配举报人昵称与被举报内容摘要。
     */
    @Transactional(readOnly = true)
    public PageResult<FlagView> listFlags(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(safePage(page), safeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<ReportFlag> result;
        if (!StringUtils.hasText(status)) {
            result = reportFlagRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            result = reportFlagRepository.findByStatusOrderByCreatedAtDesc(parseFlagStatus(status), pageable);
        }
        return PageResult.of(result.map(this::toFlagView));
    }

    /**
     * 处置举报：驳回举报（DISMISS）、下架内容（TAKE_DOWN）、禁用用户（DISABLE_USER）。
     * 举报不存在 → 1002；已处理过 → 1005 冲突。
     */
    @Transactional
    public FlagView resolveFlag(Long adminId, Long flagId, FlagHandleResult action) {
        ReportFlag flag = reportFlagRepository.findById(flagId)
                .orElseThrow(() -> BusinessException.notFound("举报不存在"));
        if (flag.getStatus() != FlagStatus.PENDING) {
            throw BusinessException.conflict("该举报已处理");
        }

        flag.setHandledBy(adminId);
        flag.setHandledAt(LocalDateTime.now());
        flag.setHandleResult(action);
        switch (action) {
            case DISMISS -> flag.setStatus(FlagStatus.DISMISSED);
            case TAKE_DOWN -> {
                flag.setStatus(FlagStatus.RESOLVED);
                takeDownFlaggedContent(adminId, flag);
            }
            case DISABLE_USER -> {
                flag.setStatus(FlagStatus.RESOLVED);
                disableFlaggedAuthor(flag);
            }
        }
        return toFlagView(reportFlagRepository.save(flag));
    }

    /** 下架被举报内容：启事置 OFFLINE（复用 takeDown 同时写审计），消息直接删除。 */
    private void takeDownFlaggedContent(Long adminId, ReportFlag flag) {
        if (flag.getTargetType() == FlagTargetType.REPORT) {
            reportRepository.findById(flag.getTargetId())
                    .ifPresent(report -> takeDown(adminId, report.getId(), flag.getReason().getLabel()));
        } else {
            messageRepository.deleteById(flag.getTargetId());
        }
    }

    /** 禁用违规者：启事违规者 = 发布者，消息违规者 = 发送者。 */
    private void disableFlaggedAuthor(ReportFlag flag) {
        Long authorId = flag.getTargetType() == FlagTargetType.REPORT
                ? reportRepository.findById(flag.getTargetId()).map(Report::getPublisherId).orElse(null)
                : messageRepository.findById(flag.getTargetId()).map(Message::getSenderId).orElse(null);
        if (authorId == null) {
            throw BusinessException.notFound("被举报内容不存在");
        }
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        author.setStatus(UserStatus.DISABLED);
        userRepository.save(author);
    }

    private void record(Long operatorId, ReportActionType action, Long reportId, String reason) {
        ReportAction record = new ReportAction();
        record.setOperatorId(operatorId);
        record.setAction(action);
        record.setReportId(reportId);
        record.setReason(reason);
        reportActionRepository.save(record);
    }

    private ReportDetail toDetail(Report report) {
        PublisherSummary publisher = userRepository.findById(report.getPublisherId())
                .map(PublisherSummary::from)
                .orElse(null);
        return ReportDetail.from(report, publisher);
    }

    private AuditLogView toView(ReportAction record) {
        String operatorNickname = userRepository.findById(record.getOperatorId())
                .map(User::getNickname)
                .orElse(null);
        String reportTitle = reportRepository.findById(record.getReportId())
                .map(Report::getTitle)
                .orElse(null);
        return new AuditLogView(
                record.getId(),
                record.getOperatorId(),
                operatorNickname,
                record.getAction(),
                record.getReportId(),
                reportTitle,
                record.getReason(),
                record.getCreatedAt());
    }

    private FlagView toFlagView(ReportFlag flag) {
        String reporterNickname = userRepository.findById(flag.getReporterId())
                .map(User::getNickname)
                .orElse(null);
        return FlagView.from(flag, reporterNickname, flagTargetSummary(flag));
    }

    /** 被举报内容摘要：启事取标题，消息取正文前若干字。 */
    private String flagTargetSummary(ReportFlag flag) {
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
        int max = 20;
        if (body == null) {
            return null;
        }
        return body.length() <= max ? body : body.substring(0, max) + "…";
    }

    private Report loadReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> BusinessException.notFound("启事不存在"));
    }

    private ReportStatus parseStatus(String status) {
        try {
            return ReportStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidParam("status：取值必须是合法的启事状态");
        }
    }

    private FlagStatus parseFlagStatus(String status) {
        try {
            return FlagStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidParam("status：取值必须是合法的举报状态");
        }
    }

    private static String requireReason(String reason, String label) {
        String trimmed = reason == null ? null : reason.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw BusinessException.invalidParam("reason：请填写" + label);
        }
        return trimmed;
    }

    private static int safePage(int page) {
        return Math.max(page, 0);
    }

    private static int safeSize(int size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
