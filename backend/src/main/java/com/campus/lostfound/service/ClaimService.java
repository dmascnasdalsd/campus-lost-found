package com.campus.lostfound.service;

import com.campus.lostfound.dto.ClaimView;
import com.campus.lostfound.dto.CreateClaimRequest;
import com.campus.lostfound.dto.ReportSummary;
import com.campus.lostfound.entity.Claim;
import com.campus.lostfound.entity.ClaimDirection;
import com.campus.lostfound.entity.ClaimStatus;
import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.ReportType;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.ClaimRepository;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认领申请相关的业务编排。
 * 只有申请人对某启事存在 PENDING / ACCEPTED 的非终态申请时才禁止重复提交；
 * 已撤回 / 已驳回的申请不阻塞再次申请。
 */
@Service
public class ClaimService {

    /** 已结束的启事不再接受新的认领申请。 */
    private static final List<ReportStatus> CLOSED_REPORT_STATUSES =
            List.of(ReportStatus.RETURNED, ReportStatus.HANDED_OVER, ReportStatus.OFFLINE);

    /** 被视为「进行中」、会阻塞重复申请的申请状态。 */
    private static final List<ClaimStatus> ACTIVE_CLAIM_STATUSES =
            List.of(ClaimStatus.PENDING, ClaimStatus.ACCEPTED);

    private final ClaimRepository claimRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ClaimService(ClaimRepository claimRepository,
                        ReportRepository reportRepository,
                        UserRepository userRepository) {
        this.claimRepository = claimRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    /**
     * 发起认领申请：校验启事存在、非本人发布、启事未结束、本人无非终态申请。
     * 申请方向由启事类型决定（招领 -> APPLY_CLAIM，失物 -> APPLY_MATCH）。
     */
    @Transactional
    public ClaimView create(Long applicantId, Long reportId, CreateClaimRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> BusinessException.notFound("启事不存在"));

        if (report.getPublisherId().equals(applicantId)) {
            throw BusinessException.forbidden("不能对自己发布的启事发起认领申请");
        }
        if (CLOSED_REPORT_STATUSES.contains(report.getStatus())) {
            throw BusinessException.invalidParam("该启事已结束，无法发起认领申请");
        }
        if (claimRepository.existsByReportIdAndApplicantIdAndStatusIn(
                reportId, applicantId, ACTIVE_CLAIM_STATUSES)) {
            throw BusinessException.conflict("你已有一条进行中的认领申请，请先撤回后再申请");
        }

        Claim claim = new Claim();
        claim.setReportId(reportId);
        claim.setApplicantId(applicantId);
        claim.setDirection(report.getType() == ReportType.FOUND
                ? ClaimDirection.APPLY_CLAIM
                : ClaimDirection.APPLY_MATCH);
        claim.setFeatureDesc(request.featureDesc().trim());
        claim.setExpectTime(request.expectTime());
        claim.setExpectPlace(trimToNull(request.expectPlace()));
        claim.setStatus(ClaimStatus.PENDING);

        return toView(claimRepository.save(claim));
    }

    /**
     * 我的认领：role=applied 返回我发起的，role=received 返回我收到的（缺省按 applied）。
     */
    @Transactional(readOnly = true)
    public List<ClaimView> list(Long userId, String role) {
        String effectiveRole = (role == null || role.isBlank()) ? "applied" : role;
        List<Claim> claims = switch (effectiveRole) {
            case "applied" -> claimRepository.findByApplicantIdOrderByCreatedAtDesc(userId);
            case "received" -> claimRepository.findReceivedByPublisherId(userId);
            default -> throw BusinessException.invalidParam("role：取值只能是 applied/received");
        };
        return toViews(claims);
    }

    /**
     * 撤回申请：仅申请人本人可撤回，且仅 PENDING 状态可撤回；撤回后置为 WITHDRAWN，用户可重新申请。
     */
    @Transactional
    public ClaimView withdraw(Long userId, Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> BusinessException.notFound("认领申请不存在"));

        if (!claim.getApplicantId().equals(userId)) {
            throw BusinessException.forbidden("只能撤回自己发起的认领申请");
        }
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw BusinessException.conflict("只有待处理的申请可以撤回");
        }

        claim.setStatus(ClaimStatus.WITHDRAWN);
        return toView(claimRepository.save(claim));
    }

    /** 单条视图：装配关联启事摘要与申请人昵称。 */
    private ClaimView toView(Claim claim) {
        ReportSummary summary = reportRepository.findById(claim.getReportId())
                .map(ReportSummary::from)
                .orElse(null);
        String nickname = userRepository.findById(claim.getApplicantId())
                .map(User::getNickname)
                .orElse(null);
        return ClaimView.from(claim, summary, nickname);
    }

    /** 批量视图：一次性取回关联启事与申请人，避免逐条查询。 */
    private List<ClaimView> toViews(List<Claim> claims) {
        if (claims.isEmpty()) {
            return List.of();
        }
        Map<Long, Report> reportsById = reportRepository
                .findAllById(claims.stream().map(Claim::getReportId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Report::getId, Function.identity()));
        Map<Long, String> nicknamesById = userRepository
                .findAllById(claims.stream().map(Claim::getApplicantId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return claims.stream().map(claim -> {
            Report report = reportsById.get(claim.getReportId());
            ReportSummary summary = report == null ? null : ReportSummary.from(report);
            return ClaimView.from(claim, summary, nicknamesById.get(claim.getApplicantId()));
        }).toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
