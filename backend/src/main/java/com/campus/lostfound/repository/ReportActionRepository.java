package com.campus.lostfound.repository;

import com.campus.lostfound.entity.ReportAction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportActionRepository extends JpaRepository<ReportAction, Long> {

    /** 审计日志：按操作时间倒序分页。 */
    Page<ReportAction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 某启事的治理动作，按时间倒序。 */
    List<ReportAction> findByReportIdOrderByCreatedAtDesc(Long reportId);
}
