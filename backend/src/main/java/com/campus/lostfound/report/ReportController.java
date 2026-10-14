package com.campus.lostfound.report;

import com.campus.lostfound.common.ApiResponse;
import com.campus.lostfound.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<PageResult<ReportSummary>> list(
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeClosed) {
        ReportSearchRequest request = new ReportSearchRequest(
                type, category, location, timeRange, startTime, endTime, keyword, sort, page, size, includeClosed);
        return ApiResponse.ok(reportService.search(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReportDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(reportService.getDetail(id));
    }

    /** 发布启事：SecurityContext 中的 principal 即登录用户 id。 */
    @PostMapping
    public ApiResponse<ReportDetail> create(@Valid @RequestBody CreateReportRequest request,
                                            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(reportService.create(userId, request));
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReportDetail> uploadImages(@PathVariable Long id,
                                                  @RequestPart("files") List<MultipartFile> files,
                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(reportService.addImages(userId, id, files));
    }
}
