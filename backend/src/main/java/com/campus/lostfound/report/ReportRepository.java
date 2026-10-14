package com.campus.lostfound.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    /** 某用户发布的全部启事（含终态），排序由调用方通过 Pageable 指定。 */
    Page<Report> findByPublisherId(Long publisherId, Pageable pageable);
}
