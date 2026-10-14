package com.campus.lostfound.repository;

import com.campus.lostfound.entity.Message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 会话消息，按时间升序（同一时刻再按 id 升序保证稳定）。 */
    List<Message> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);
}
