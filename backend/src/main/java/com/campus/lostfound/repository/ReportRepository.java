package com.campus.lostfound.repository;

import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    /** 某用户发布的全部启事（含终态），排序由调用方通过 Pageable 指定。 */
    Page<Report> findByPublisherId(Long publisherId, Pageable pageable);

    /** 管理端复核队列：按状态过滤，排序由调用方通过 Pageable 指定。 */
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    /** 统计与导出：创建时间落在 [from, to] 闭区间内的启事，按创建时间升序。 */
    List<Report> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime from, LocalDateTime to);
}
