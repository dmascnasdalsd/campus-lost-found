package com.campus.lostfound.repository;

import com.campus.lostfound.entity.FlagStatus;
import com.campus.lostfound.entity.FlagTargetType;
import com.campus.lostfound.entity.ReportFlag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportFlagRepository extends JpaRepository<ReportFlag, Long> {

    /** 举报列表：按举报时间倒序分页。 */
    Page<ReportFlag> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按处理状态过滤，排序由调用方通过 Pageable 指定。 */
    Page<ReportFlag> findByStatusOrderByCreatedAtDesc(FlagStatus status, Pageable pageable);

    /** 某目标（启事 / 消息）收到的全部举报。 */
    List<ReportFlag> findByTargetTypeAndTargetId(FlagTargetType targetType, Long targetId);
}
