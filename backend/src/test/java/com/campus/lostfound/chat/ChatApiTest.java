package com.campus.lostfound.chat;

import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.ReportType;
import com.campus.lostfound.entity.UserRole;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.service.JwtService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工单 07：站内会话（列表 / 消息收发 / 越权）的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「返回什么、库中最终留下什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private long publisherId;
    private long applicantId;
    private long strangerId;
    private String publisherToken;
    private String applicantToken;
    private String strangerToken;

    @BeforeEach
    void 每个用例前清空数据库() {
        jdbcTemplate.execute("DELETE FROM report_image");
        jdbcTemplate.execute("DELETE FROM message");
        jdbcTemplate.execute("DELETE FROM chat_session");
        jdbcTemplate.execute("DELETE FROM claim");
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM report_action");
        jdbcTemplate.execute("DELETE FROM report_flag");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM app_user");

        publisherId = insertUser("2021001", "发布者");
        applicantId = insertUser("2021002", "申请人");
        strangerId = insertUser("2021003", "路人");
        publisherToken = jwtService.generate(publisherId, UserRole.USER);
        applicantToken = jwtService.generate(applicantId, UserRole.USER);
        strangerToken = jwtService.generate(strangerId, UserRole.USER);
    }

    @Test
    void 双方都能在我参与的会话中看到该会话() throws Exception {
        long sessionId = openSession();

        JsonNode publisherChats = getChats(publisherToken).body().path("data");
        assertThat(publisherChats).hasSize(1);
        assertThat(publisherChats.get(0).path("id").asLong()).isEqualTo(sessionId);
        assertThat(publisherChats.get(0).path("peerNickname").asText()).isEqualTo("申请人");
        assertThat(publisherChats.get(0).path("report").path("title").asText()).isEqualTo("捡到的校园卡");

        JsonNode applicantChats = getChats(applicantToken).body().path("data");
        assertThat(applicantChats).hasSize(1);
        assertThat(applicantChats.get(0).path("peerNickname").asText()).isEqualTo("发布者");

        assertThat(getChats(strangerToken).body().path("data")).isEmpty();
    }

    @Test
    void 参与方可收发消息并按时间升序读取() throws Exception {
        long sessionId = openSession();

        assertThat(sendMessage(sessionId, "你好，我捡到了你的校园卡", publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(sendMessage(sessionId, "太好了，什么时候方便拿？", applicantToken).status())
                .isEqualTo(HttpStatus.OK.value());

        HttpResult messages = getMessages(sessionId, applicantToken);
        assertThat(messages.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode data = messages.body().path("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("body").asText()).isEqualTo("你好，我捡到了你的校园卡");
        assertThat(data.get(0).path("senderNickname").asText()).isEqualTo("发布者");
        assertThat(data.get(1).path("body").asText()).isEqualTo("太好了，什么时候方便拿？");
        assertThat(data.get(1).path("senderNickname").asText()).isEqualTo("申请人");
        assertThat(messageCount()).isEqualTo(2);
    }

    @Test
    void 非参与方读取消息返回越权码() throws Exception {
        long sessionId = openSession();

        HttpResult result = getMessages(sessionId, strangerToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
    }

    @Test
    void 非参与方发送消息返回越权码且不落库() throws Exception {
        long sessionId = openSession();

        HttpResult result = sendMessage(sessionId, "我不该能发言", strangerToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
        assertThat(messageCount()).isZero();
    }

    @Test
    void 已关闭会话发送消息返回可辨识冲突码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long sessionId = openSessionOn(reportId);

        putStatus(reportId, "RETURNED", publisherToken);

        HttpResult result = sendMessage(sessionId, "还能发吗", publisherToken);
        assertThat(result.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1005);
        assertThat(messageCount()).isZero();
    }

    @Test
    void 未登录访问会话返回未授权() {
        HttpResult result = getChats(null);

        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1003);
    }

    // ---------- 请求辅助 ----------

    private long openSession() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        return openSessionOn(reportId);
    }

    /** 创建启事 -> 申请 -> 接受，返回生成会话的 id。 */
    private long openSessionOn(long reportId) throws Exception {
        HttpResult claim = exchange(HttpMethod.POST, "/api/reports/" + reportId + "/claims",
                "{\"featureDesc\":\"校园卡卡号后四位是 1234\"}", applicantToken);
        long claimId = claim.body().path("data").path("id").asLong();
        HttpResult accepted = exchange(HttpMethod.POST, "/api/claims/" + claimId + "/accept", null, publisherToken);
        assertThat(accepted.status()).isEqualTo(HttpStatus.OK.value());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM chat_session WHERE claim_id = ?", Long.class, claimId);
    }

    private HttpResult getChats(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/chats", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private HttpResult getMessages(long sessionId, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/chats/" + sessionId + "/messages", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private HttpResult sendMessage(long sessionId, String body, String token) throws Exception {
        return exchange(HttpMethod.POST, "/api/chats/" + sessionId + "/messages",
                "{\"body\":\"" + body + "\"}", token);
    }

    private HttpResult putStatus(long reportId, String status, String token) throws Exception {
        return exchange(HttpMethod.PUT, "/api/reports/" + reportId + "/status",
                "{\"status\":\"" + status + "\"}", token);
    }

    private HttpResult exchange(HttpMethod method, String path, String payload, String token)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                path, method, new HttpEntity<>(payload, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private JsonNode readTree(String raw) {
        try {
            return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private long saveReport(long ownerId, String title, ReportType type, ReportStatus status) {
        Report report = new Report();
        report.setType(type);
        report.setTitle(title);
        report.setCategory("校园卡");
        report.setLocation("图书馆");
        report.setEventTime(LocalDateTime.now().minusHours(1));
        report.setStatus(status);
        report.setPublisherId(ownerId);
        report.setDescription("描述");
        return reportRepository.save(report).getId();
    }

    private int messageCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message", Integer.class);
    }

    private long insertUser(String studentNo, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO app_user (student_no, nickname, password_hash, role, status, contact_public, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', 'ACTIVE', 0, NOW(6), NOW(6))
                """, studentNo, nickname, "$2a$10$abcdefghijklmnopqrstuv");
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE student_no = ?", Long.class, studentNo);
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
