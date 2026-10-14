package com.campus.lostfound.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后端唯一测试接缝的样板：通过真实 HTTP 请求断言外部行为。
 * 只断言「发出请求后返回什么、库里最终留下什么」，不断言内部实现。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private long publisherId;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM report_image");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM app_user");
        publisherId = insertPublisher();
    }

    @Test
    void 首页只返回仍在招领中的启事且置顶优先() throws Exception {
        saveReport("校园卡", ReportStatus.OPEN, false);
        saveReport("AirPods 耳机", ReportStatus.OPEN, false);
        saveReport("已归还的水杯", ReportStatus.RETURNED, false);
        saveReport("认领一批校园卡", ReportStatus.OPEN, true);

        JsonNode body = getReports();

        assertThat(body.path("code").asInt()).isZero();
        JsonNode data = body.path("data");
        assertThat(data.path("total").asLong()).isEqualTo(3);
        assertThat(data.path("items")).hasSize(3);
        assertThat(data.path("items").get(0).path("title").asText()).isEqualTo("认领一批校园卡");
        assertThat(data.path("items").get(0).path("pinned").asBoolean()).isTrue();
        assertThat(data.path("items").get(0).path("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void 无启事时返回空分页结构() throws Exception {
        JsonNode body = getReports();

        JsonNode data = body.path("data");
        assertThat(body.path("code").asInt()).isZero();
        assertThat(data.path("total").asLong()).isZero();
        assertThat(data.path("items")).isEmpty();
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);
    }

    private JsonNode getReports() throws Exception {
        String raw = restTemplate.getForObject("/api/reports", String.class);
        return objectMapper.readTree(raw);
    }

    private void saveReport(String title, ReportStatus status, boolean pinned) {
        Report report = new Report();
        report.setType(ReportType.LOST);
        report.setTitle(title);
        report.setCategory("其他");
        report.setLocation("图书馆");
        report.setEventTime(LocalDateTime.now().minusHours(3));
        report.setDescription("描述");
        report.setStatus(status);
        report.setPublisherId(publisherId);
        report.setPinned(pinned);
        reportRepository.save(report);
    }

    private long insertPublisher() {
        jdbcTemplate.update("""
                INSERT INTO app_user (student_no, nickname, password_hash, role, status, contact_public, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', 'ACTIVE', 0, NOW(6), NOW(6))
                """, "2021001", "测试用户", "$2a$10$abcdefghijklmnopqrstuv");
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE student_no = ?", Long.class, "2021001");
    }
}
