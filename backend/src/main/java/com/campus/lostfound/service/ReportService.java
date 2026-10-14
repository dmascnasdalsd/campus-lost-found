package com.campus.lostfound.service;

import com.campus.lostfound.dto.CreateReportRequest;
import com.campus.lostfound.dto.PageResult;
import com.campus.lostfound.dto.PublisherSummary;
import com.campus.lostfound.dto.ReportDetail;
import com.campus.lostfound.dto.ReportSearchRequest;
import com.campus.lostfound.dto.ReportSummary;
import com.campus.lostfound.dto.UpdateReportRequest;
import com.campus.lostfound.dto.UpdateStatusRequest;
import com.campus.lostfound.entity.ChatSession;
import com.campus.lostfound.entity.ChatSessionStatus;
import com.campus.lostfound.entity.Claim;
import com.campus.lostfound.entity.ClaimStatus;
import com.campus.lostfound.entity.Custody;
import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportImage;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.ReportType;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.exception.ErrorCodes;
import com.campus.lostfound.repository.ChatSessionRepository;
import com.campus.lostfound.repository.ClaimRepository;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.repository.ReportSpecifications;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.support.Texts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_IMAGES_PER_REPORT = 3;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final String SORT_EVENT_TIME = "eventTime";
    /** 发布后可编辑 / 删除的时间窗。 */
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(30);

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ClaimRepository claimRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final Path uploadDir;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         ClaimRepository claimRepository,
                         ChatSessionRepository chatSessionRepository,
                         @Value("${app.upload.dir}") String uploadDir) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.claimRepository = claimRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 列表检索：按类型 / 分类 / 地点 / 时间窗 / 关键词过滤，默认隐藏已结束的启事。
     * 排序始终以置顶优先，次序键为最新发布（createdAt）或最近事件时间（eventTime）。
     * 只读事务保持 session 到映射结束，以便摘要读取缩略图路径（open-in-view 已关闭）。
     */
    @Transactional(readOnly = true)
    public PageResult<ReportSummary> search(ReportSearchRequest request) {
        int page = request.page() == null ? 0 : Math.max(request.page(), 0);
        int size = request.size() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(request.size(), 1), MAX_PAGE_SIZE);

        ReportSpecifications.TimeWindow window = resolveTimeWindow(request);
        Specification<Report> specification = ReportSpecifications.of(request, window);
        Sort sort = resolveSort(request.sort());

        Page<Report> result = reportRepository.findAll(specification, PageRequest.of(page, size, sort));
        return PageResult.of(result.map(ReportSummary::from));
    }

    private ReportSpecifications.TimeWindow resolveTimeWindow(ReportSearchRequest request) {
        if (!StringUtils.hasText(request.timeRange())) {
            return new ReportSpecifications.TimeWindow(null, null);
        }
        LocalDateTime now = LocalDateTime.now();
        return switch (request.timeRange()) {
            case "today" -> new ReportSpecifications.TimeWindow(now.toLocalDate().atStartOfDay(), now);
            case "3d" -> new ReportSpecifications.TimeWindow(now.minusDays(3), now);
            case "7d" -> new ReportSpecifications.TimeWindow(now.minusDays(7), now);
            case "30d" -> new ReportSpecifications.TimeWindow(now.minusDays(30), now);
            case "custom" -> new ReportSpecifications.TimeWindow(request.startTime(), request.endTime());
            default -> throw BusinessException.invalidParam("timeRange：取值只能是 today/3d/7d/30d/custom");
        };
    }

    private Sort resolveSort(String sort) {
        Sort.Order pinnedFirst = Sort.Order.desc("pinned");
        Sort.Order tieBreaker = Sort.Order.desc("id");
        return SORT_EVENT_TIME.equals(sort)
                ? Sort.by(pinnedFirst, Sort.Order.desc("eventTime"), tieBreaker)
                : Sort.by(pinnedFirst, Sort.Order.desc("createdAt"), tieBreaker);
    }

    /**
     * 发布启事：发布即上线（status=OPEN），publisherId 取当前登录用户。
     */
    @Transactional
    public ReportDetail create(Long publisherId, CreateReportRequest request) {
        validateVocabulary(request);

        Report report = new Report();
        report.setType(request.type());
        report.setTitle(request.title().trim());
        report.setCategory(request.category());
        report.setLocation(request.location());
        report.setLocationDetail(Texts.trimToNull(request.locationDetail()));
        report.setEventTime(request.eventTime());
        report.setDescription(Texts.trimToNull(request.description()));
        report.setStatus(ReportStatus.OPEN);
        report.setPublisherId(publisherId);

        if (request.type() == ReportType.FOUND) {
            report.setCustody(request.custody());
            if (request.custody() == Custody.AT_SECURITY) {
                String handoverNo = Texts.trimToNull(request.handoverNo());
                if (handoverNo == null) {
                    throw BusinessException.invalidParam("handoverNo：移交保卫处时必须填写移交单号");
                }
                report.setHandoverNo(handoverNo);
            } else {
                report.setHandoverNo(null);
            }
        } else {
            // 失物启事不使用保管方式与移交单号。
            report.setCustody(null);
            report.setHandoverNo(null);
        }

        return toDetail(reportRepository.save(report));
    }

    /**
     * 详情：写事务，使浏览量 +1；响应中 viewCount 为自增后的值。
     */
    @Transactional
    public ReportDetail getDetail(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("启事不存在"));
        report.setViewCount(report.getViewCount() + 1);
        return toDetail(report);
    }

    /**
     * 上传图片：写入本地磁盘目录，库中只存元数据；仅启事发布者可上传，总张数 1～3。
     */
    @Transactional
    public ReportDetail addImages(Long userId, Long reportId, List<MultipartFile> files) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> BusinessException.notFound("启事不存在"));
        if (!report.getPublisherId().equals(userId)) {
            throw BusinessException.forbidden("只能为自己的启事上传图片");
        }

        List<MultipartFile> incoming = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (incoming.isEmpty()) {
            throw BusinessException.invalidParam("files：请至少选择 1 张图片");
        }
        if (report.getImages().size() + incoming.size() > MAX_IMAGES_PER_REPORT) {
            throw BusinessException.invalidParam("files：图片总数不能超过 " + MAX_IMAGES_PER_REPORT + " 张");
        }

        createUploadDir();

        int sortOrder = report.getImages().size();
        for (MultipartFile file : incoming) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw BusinessException.invalidParam("files：单张图片不能超过 5MB");
            }
            report.getImages().add(storeImage(report, file, sortOrder++));
        }

        return toDetail(reportRepository.save(report));
    }

    /**
     * 修改启事：仅发布者或管理员可操作，且必须在发布后 30 分钟内。
     * 采用 PATCH 语义 —— 请求体中为 null 的字段表示不改动。
     */
    @Transactional
    public ReportDetail update(Long userId, boolean admin, Long reportId, UpdateReportRequest request) {
        Report report = loadReport(reportId);
        ensureOwner(report, userId, admin);
        ensureWithinEditWindow(report);

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw BusinessException.invalidParam("title：请填写物品名称");
            }
            if (title.length() > 128) {
                throw BusinessException.invalidParam("title：物品名称不超过 128 字");
            }
            report.setTitle(title);
        }
        if (request.category() != null) {
            if (!ReportVocabulary.isValidCategory(request.category())) {
                throw BusinessException.invalidParam("category：分类必须取自受控词表");
            }
            report.setCategory(request.category());
        }
        if (request.location() != null) {
            if (!ReportVocabulary.isValidLocation(request.location())) {
                throw BusinessException.invalidParam("location：地点必须取自受控词表");
            }
            report.setLocation(request.location());
        }
        if (request.locationDetail() != null) {
            report.setLocationDetail(Texts.trimToNull(request.locationDetail()));
        }
        if (request.eventTime() != null) {
            report.setEventTime(request.eventTime());
        }
        if (request.description() != null) {
            report.setDescription(Texts.trimToNull(request.description()));
        }
        if (report.getType() == ReportType.FOUND) {
            if (request.custody() != null) {
                report.setCustody(request.custody());
                if (request.custody() == Custody.SELF) {
                    report.setHandoverNo(null);
                }
            }
            if (request.handoverNo() != null) {
                report.setHandoverNo(Texts.trimToNull(request.handoverNo()));
            }
            if (report.getCustody() == Custody.AT_SECURITY && report.getHandoverNo() == null) {
                throw BusinessException.invalidParam("handoverNo：移交保卫处时必须填写移交单号");
            }
        }

        return toDetail(reportRepository.save(report));
    }

    /**
     * 删除启事：权限与时间窗同「修改启事」；图片经 cascade + orphanRemoval 一并删除。
     */
    @Transactional
    public void delete(Long userId, boolean admin, Long reportId) {
        Report report = loadReport(reportId);
        ensureOwner(report, userId, admin);
        ensureWithinEditWindow(report);
        reportRepository.delete(report);
    }

    /**
     * 变更启事状态：仅发布者或管理员可操作，且不受 30 分钟编辑窗口限制。
     * v1 只接受 RETURNED（已归还）与 HANDED_OVER（已移交，仅招领启事、必须带移交单号）。
     */
    @Transactional
    public ReportDetail updateStatus(Long userId, boolean admin, Long reportId, UpdateStatusRequest request) {
        ReportStatus status = request.status();
        if (status != ReportStatus.RETURNED && status != ReportStatus.HANDED_OVER) {
            throw BusinessException.invalidParam("status：只能变更为 RETURNED（已归还）或 HANDED_OVER（已移交）");
        }

        Report report = loadReport(reportId);
        ensureOwner(report, userId, admin);

        if (status == ReportStatus.HANDED_OVER) {
            if (report.getType() != ReportType.FOUND) {
                throw BusinessException.invalidParam("status：HANDED_OVER 仅招领启事可用");
            }
            String handoverNo = Texts.trimToNull(request.handoverNo());
            if (handoverNo == null) {
                throw BusinessException.invalidParam("handoverNo：已移交必须填写移交单号");
            }
            report.setHandoverNo(handoverNo);
        }

        report.setStatus(status);
        ReportDetail detail = toDetail(reportRepository.save(report));

        // 交还完成 / 移交完成：该启事下已接受的认领申请置为 CLOSED，对应会话一并关闭。
        closeClaimsAndChatSessions(reportId);

        return detail;
    }

    /**
     * 启事交还完成后：把该启事下 ACCEPTED 与 PENDING 的认领申请一并置为 CLOSED，
     * 并把对应会话置为 CLOSED（closedAt=now）。
     */
    private void closeClaimsAndChatSessions(Long reportId) {
        LocalDateTime now = LocalDateTime.now();
        List<Claim> openClaims = new ArrayList<>(
                claimRepository.findByReportIdAndStatus(reportId, ClaimStatus.ACCEPTED));
        openClaims.addAll(claimRepository.findByReportIdAndStatus(reportId, ClaimStatus.PENDING));
        for (Claim claim : openClaims) {
            claim.setStatus(ClaimStatus.CLOSED);
            claim.setHandledAt(now);
            claimRepository.save(claim);
        }
        for (ChatSession session : chatSessionRepository.findByReportId(reportId)) {
            if (session.getStatus() != ChatSessionStatus.CLOSED) {
                session.setStatus(ChatSessionStatus.CLOSED);
                session.setClosedAt(now);
                chatSessionRepository.save(session);
            }
        }
    }

    /**
     * 当前用户的全部启事（含已归还 / 已移交 / 已下架等终态），按 createdAt 倒序。
     */
    @Transactional(readOnly = true)
    public PageResult<ReportSummary> listMine(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Report> result = reportRepository.findByPublisherId(userId, pageable);
        return PageResult.of(result.map(ReportSummary::from));
    }

    private Report loadReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> BusinessException.notFound("启事不存在"));
    }

    private void ensureOwner(Report report, Long userId, boolean admin) {
        if (!admin && !report.getPublisherId().equals(userId)) {
            throw BusinessException.forbidden("只能操作自己发布的启事");
        }
    }

    private void ensureWithinEditWindow(Report report) {
        LocalDateTime createdAt = report.getCreatedAt();
        if (createdAt != null && createdAt.plus(EDIT_WINDOW).isBefore(LocalDateTime.now())) {
            throw BusinessException.conflict("启事发布已超过 30 分钟，不能再编辑或删除");
        }
    }

    private void validateVocabulary(CreateReportRequest request) {
        if (!ReportVocabulary.isValidCategory(request.category())) {
            throw BusinessException.invalidParam("category：分类必须取自受控词表");
        }
        if (!ReportVocabulary.isValidLocation(request.location())) {
            throw BusinessException.invalidParam("location：地点必须取自受控词表");
        }
        if (request.type() == ReportType.FOUND && request.custody() == null) {
            throw BusinessException.invalidParam("custody：招领启事必须选择保管方式（自留或已交保卫处）");
        }
    }

    private ReportImage storeImage(Report report, MultipartFile file, int sortOrder) {
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? StringUtils.cleanPath(file.getOriginalFilename())
                : "image";
        String storedName = UUID.randomUUID() + extensionOf(originalName);
        try {
            file.transferTo(uploadDir.resolve(storedName));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, 500, "图片写入失败");
        }

        ReportImage image = new ReportImage();
        image.setReport(report);
        image.setPath("/uploads/" + storedName);
        image.setOriginalName(originalName);
        image.setMime(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream");
        image.setSizeBytes(file.getSize());
        image.setSortOrder(sortOrder);
        return image;
    }

    private void createUploadDir() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, 500, "上传目录创建失败");
        }
    }

    private ReportDetail toDetail(Report report) {
        PublisherSummary publisher = userRepository.findById(report.getPublisherId())
                .map(PublisherSummary::from)
                .orElse(null);
        return ReportDetail.from(report, publisher);
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".jpg";
        }
        String extension = filename.substring(dot).toLowerCase();
        return extension.length() > 16 ? ".jpg" : extension;
    }
}
