package com.campus.lostfound.service;

import com.campus.lostfound.dto.AdminStatsView;
import com.campus.lostfound.dto.AuditLogView;
import com.campus.lostfound.dto.DailyStatView;
import com.campus.lostfound.dto.FlagView;
import com.campus.lostfound.dto.NameCountView;
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
import com.campus.lostfound.entity.ReportType;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员内容治理：复核队列、通过 / 驳回 / 下架 / 置顶，并记录审计。
 * 角色校验由 SecurityConfig 的 /api/admin/** 规则负责，Service 只管业务编排。
 */
@Service
public class AdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    /** 统计窗口：week=最近 7 天，month=最近 30 天。 */
    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;

    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String EXPORT_HEADER =
            "ID,类型,标题,分类,地点,事件时间,状态,是否已归还,发布者,置顶,创建时间";

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

    /**
     * 统计看板：按统计窗口（week=最近 7 天 / month=最近 30 天）聚合启事。
     * range 缺省或为空 → week；非法值 → 1001。
     * 窗口为 [今天-(n-1)天 00:00, 现在]，全部基于真实数据聚合，无硬编码。
     */
    @Transactional(readOnly = true)
    public AdminStatsView stats(String range) {
        int days = windowDays(range);
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(days - 1L);

        List<Report> reports = reportRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(
                fromDate.atStartOfDay(), LocalDateTime.now());

        long totalReports = reports.size();
        long returnedReports = reports.stream().filter(r -> isReturned(r.getStatus())).count();
        double returnRate = totalReports == 0
                ? 0.0
                : Math.round(returnedReports * 1000.0 / totalReports) / 10.0;

        return new AdminStatsView(
                range,
                fromDate,
                toDate,
                totalReports,
                returnedReports,
                returnRate,
                buildDaily(reports, fromDate, toDate),
                groupCount(reports, Report::getLocation),
                groupCount(reports, Report::getCategory),
                buildHourly(reports));
    }

    /**
     * 导出 CSV：某时间段内（按 created_at 闭区间）的启事记录。
     * from/to 缺省 = 最近 30 天；返回带 UTF-8 BOM 的字节流，便于 Excel 正确显示中文。
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(LocalDate from, LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(MONTH_DAYS - 1L);

        List<Report> reports = reportRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(
                fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX));
        Map<Long, String> nicknames = loadNicknames(reports);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，帮助 Excel 识别编码。
        sb.append('\uFEFF');
        sb.append(EXPORT_HEADER).append("\r\n");
        for (Report report : reports) {
            sb.append(report.getId())
                    .append(',').append(csv(typeLabel(report.getType())))
                    .append(',').append(csv(report.getTitle()))
                    .append(',').append(csv(report.getCategory()))
                    .append(',').append(csv(report.getLocation()))
                    .append(',').append(csv(formatTime(report.getEventTime())))
                    .append(',').append(csv(statusLabel(report.getStatus())))
                    .append(',').append(csv(isReturned(report.getStatus()) ? "是" : "否"))
                    .append(',').append(csv(nicknames.getOrDefault(report.getPublisherId(), "")))
                    .append(',').append(csv(report.isPinned() ? "是" : "否"))
                    .append(',').append(csv(formatTime(report.getCreatedAt())))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 解析统计窗口天数：week→7、month→30、缺省→week，非法值 → 1001。 */
    private static int windowDays(String range) {
        if (range == null || range.isBlank() || "week".equals(range)) {
            return WEEK_DAYS;
        }
        if ("month".equals(range)) {
            return MONTH_DAYS;
        }
        throw BusinessException.invalidParam("range：取值必须是 week 或 month");
    }

    private static boolean isReturned(ReportStatus status) {
        return status == ReportStatus.RETURNED || status == ReportStatus.HANDED_OVER;
    }

    /** 每日序列：窗口内每一天都出现，无数据的日期补 0。 */
    private static List<DailyStatView> buildDaily(List<Report> reports, LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, long[]> counts = new TreeMap<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            counts.put(date, new long[2]);
        }
        for (Report report : reports) {
            long[] bucket = counts.get(report.getCreatedAt().toLocalDate());
            if (bucket != null) {
                bucket[0]++;
                if (isReturned(report.getStatus())) {
                    bucket[1]++;
                }
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new DailyStatView(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    /** 按名称（地点 / 分类）分组计数，count 倒序，同数按名称升序。 */
    private static List<NameCountView> groupCount(List<Report> reports, Function<Report, String> keyFn) {
        Map<String, Long> counts = new HashMap<>();
        for (Report report : reports) {
            counts.merge(keyFn.apply(report), 1L, Long::sum);
        }
        Comparator<Map.Entry<String, Long>> order =
                Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey);
        return counts.entrySet().stream()
                .sorted(order)
                .map(entry -> new NameCountView(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 高发时段：按 event_time 的小时分组，0~23 全量补 0，name 形如 "09:00"。 */
    private static List<NameCountView> buildHourly(List<Report> reports) {
        long[] counts = new long[24];
        for (Report report : reports) {
            if (report.getEventTime() != null) {
                counts[report.getEventTime().getHour()]++;
            }
        }
        List<NameCountView> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            result.add(new NameCountView(String.format("%02d:00", hour), counts[hour]));
        }
        return result;
    }

    private Map<Long, String> loadNicknames(List<Report> reports) {
        Set<Long> publisherIds = reports.stream().map(Report::getPublisherId).collect(Collectors.toSet());
        Map<Long, String> nicknames = new HashMap<>();
        for (User user : userRepository.findAllById(publisherIds)) {
            nicknames.put(user.getId(), user.getNickname());
        }
        return nicknames;
    }

    private static String typeLabel(ReportType type) {
        return type == ReportType.LOST ? "失物" : "招领";
    }

    private static String statusLabel(ReportStatus status) {
        return switch (status) {
            case OPEN -> "进行中";
            case PENDING_REVIEW -> "待复核";
            case CLAIMING -> "认领中";
            case RETURNED -> "已归还";
            case HANDED_OVER -> "已移交";
            case OFFLINE -> "已下架";
            case REJECTED -> "已驳回";
        };
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(EXPORT_TIME_FORMAT);
    }

    /** CSV 字段转义：含逗号、引号或换行时加双引号并转义内部引号。 */
    private static String csv(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
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
