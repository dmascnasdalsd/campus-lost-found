package com.campus.lostfound.report;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 列表检索的动态查询条件（JPA Specification），保持可读且不拼 SQL 字符串。
 * 默认隐藏的终态（已归还 / 已移交 / 已下架 / 已驳回）通过 includeClosed 开关控制是否纳入。
 */
final class ReportSpecifications {

    static final List<ReportStatus> CLOSED_STATUSES = List.of(
            ReportStatus.RETURNED,
            ReportStatus.HANDED_OVER,
            ReportStatus.OFFLINE,
            ReportStatus.REJECTED);

    static Specification<Report> of(ReportSearchRequest request, TimeWindow window) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.type() != null) {
                predicates.add(cb.equal(root.get("type"), request.type()));
            }
            if (StringUtils.hasText(request.category())) {
                predicates.add(cb.equal(root.get("category"), request.category().trim()));
            }
            if (StringUtils.hasText(request.location())) {
                predicates.add(cb.equal(root.get("location"), request.location().trim()));
            }
            if (window.start() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), window.start()));
            }
            if (window.end() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventTime"), window.end()));
            }
            if (StringUtils.hasText(request.keyword())) {
                String pattern = "%" + request.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)));
            }
            if (!Boolean.TRUE.equals(request.includeClosed())) {
                predicates.add(cb.not(root.get("status").in(CLOSED_STATUSES)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ReportSpecifications() {
    }

    /** 事件时间过滤窗口，两端均可为空（表示该端不设限）。 */
    record TimeWindow(LocalDateTime start, LocalDateTime end) {
    }
}
