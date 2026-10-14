package com.campus.lostfound.service;

import com.campus.lostfound.dto.ChatSessionView;
import com.campus.lostfound.dto.MessageView;
import com.campus.lostfound.dto.ReportSummary;
import com.campus.lostfound.entity.ChatSession;
import com.campus.lostfound.entity.ChatSessionStatus;
import com.campus.lostfound.entity.Message;
import com.campus.lostfound.entity.NotificationTargetType;
import com.campus.lostfound.entity.NotificationType;
import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.ChatSessionRepository;
import com.campus.lostfound.repository.MessageRepository;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内会话的业务编排：会话只对发布者与申请人双方可见。
 * 会话与消息均只存外键 id，本类显式装配启事摘要与昵称（open-in-view 已关闭，读取需在事务内）。
 */
@Service
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ChatService(ChatSessionRepository chatSessionRepository,
                       MessageRepository messageRepository,
                       ReportRepository reportRepository,
                       UserRepository userRepository,
                       NotificationService notificationService) {
        this.chatSessionRepository = chatSessionRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * 我参与的会话（我作为发布者或申请人），按创建时间倒序；装配启事摘要与对方昵称。
     */
    @Transactional(readOnly = true)
    public List<ChatSessionView> list(Long userId) {
        List<ChatSession> sessions =
                chatSessionRepository.findByOwnerIdOrApplicantIdOrderByCreatedAtDesc(userId, userId);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<Long, Report> reportsById = reportRepository
                .findAllById(sessions.stream().map(ChatSession::getReportId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Report::getId, Function.identity()));

        // 对方 = owner 与 applicant 中不是我的那个。
        List<Long> peerIds = sessions.stream()
                .map(session -> session.getOwnerId().equals(userId) ? session.getApplicantId() : session.getOwnerId())
                .distinct()
                .toList();
        Map<Long, String> nicknamesById = userRepository.findAllById(peerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return sessions.stream().map(session -> {
            Report report = reportsById.get(session.getReportId());
            ReportSummary summary = report == null ? null : ReportSummary.from(report);
            Long peerId = session.getOwnerId().equals(userId)
                    ? session.getApplicantId()
                    : session.getOwnerId();
            return ChatSessionView.from(session, summary, nicknamesById.get(peerId));
        }).toList();
    }

    /**
     * 会话消息：非参与方一律越权；按时间升序返回，装配发送者昵称。
     */
    @Transactional(readOnly = true)
    public List<MessageView> messages(Long userId, Long sessionId) {
        ChatSession session = loadSession(sessionId);
        ensureParticipant(session, userId);

        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nicknamesById = userRepository
                .findAllById(messages.stream().map(Message::getSenderId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return messages.stream()
                .map(message -> MessageView.from(message, nicknamesById.get(message.getSenderId())))
                .toList();
    }

    /**
     * 发送消息：非参与方越权；已关闭的会话不可再发消息。
     */
    @Transactional
    public MessageView send(Long userId, Long sessionId, String body) {
        ChatSession session = loadSession(sessionId);
        ensureParticipant(session, userId);

        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw BusinessException.conflict("会话已关闭，无法发送消息");
        }

        Message message = new Message();
        message.setSessionId(sessionId);
        message.setSenderId(userId);
        message.setBody(body.trim());
        Message saved = messageRepository.save(message);

        // 通知会话另一方：收到新消息。
        Long peerId = session.getOwnerId().equals(userId)
                ? session.getApplicantId()
                : session.getOwnerId();
        notificationService.notify(peerId, NotificationType.MESSAGE_RECEIVED,
                NotificationTargetType.SESSION, sessionId);

        String nickname = userRepository.findById(userId).map(User::getNickname).orElse(null);
        return MessageView.from(saved, nickname);
    }

    private ChatSession loadSession(Long sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.notFound("会话不存在"));
    }

    private void ensureParticipant(ChatSession session, Long userId) {
        if (!session.getOwnerId().equals(userId) && !session.getApplicantId().equals(userId)) {
            throw BusinessException.forbidden("你无权访问该会话");
        }
    }
}
