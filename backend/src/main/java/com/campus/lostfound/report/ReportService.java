package com.campus.lostfound.report;

import com.campus.lostfound.common.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * 首页列表：只返回仍在招领中的启事，置顶优先、新发布在前。
     * 只读事务保持 session 到映射结束，以便摘要读取缩略图路径（open-in-view 已关闭）。
     */
    @Transactional(readOnly = true)
    public PageResult<ReportSummary> listOpenReports(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"));
        Page<Report> result = reportRepository.findByStatus(ReportStatus.OPEN, PageRequest.of(safePage, safeSize, sort));
        return PageResult.of(result.map(ReportSummary::from));
    }
}
