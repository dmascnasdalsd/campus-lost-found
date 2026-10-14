package com.campus.lostfound.service;

import com.campus.lostfound.dto.AuditLogView;
import com.campus.lostfound.dto.PageResult;
import com.campus.lostfound.dto.PublisherSummary;
import com.campus.lostfound.dto.ReportDetail;
import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportAction;
import com.campus.lostfound.entity.ReportActionType;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.ReportActionRepository;
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

    public AdminService(ReportRepository reportRepository,
                        UserRepository userRepository,
                        ReportActionRepository reportActionRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.reportActionRepository = reportActionRepository;
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
        return toDetail(reportRepository.save(report));
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
        return toDetail(reportRepository.save(report));
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
