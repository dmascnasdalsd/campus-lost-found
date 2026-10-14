package com.campus.lostfound.report;

import com.campus.lostfound.auth.JwtService;
import com.campus.lostfound.auth.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
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

    @Autowired
    private JwtService jwtService;

    private long publisherId;
    private String token;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM report_image");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM app_user");
        publisherId = insertPublisher();
        token = jwtService.generate(publisherId, UserRole.USER);
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

    @Test
    void 非法分类被拒并返回字段级错误() throws Exception {
        String payload = """
                {"type":"LOST","title":"校园卡","category":"不存在的分类","location":"图书馆",
                 "eventTime":"2026-10-14T08:30:00","description":"描述"}
                """;

        ResponseEntity<String> response = postJson("/api/reports", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode node = objectMapper.readTree(response.getBody());
        assertThat(node.path("code").asInt()).isEqualTo(1001);
        assertThat(node.path("message").asText()).contains("category");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report", Integer.class)).isZero();
    }

    @Test
    void 招领启事选已交保卫处却缺移交单号被拒() throws Exception {
        String payload = """
                {"type":"FOUND","title":"耳机","category":"耳机","location":"图书馆",
                 "eventTime":"2026-10-14T08:30:00","custody":"AT_SECURITY"}
                """;

        ResponseEntity<String> response = postJson("/api/reports", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode node = objectMapper.readTree(response.getBody());
        assertThat(node.path("code").asInt()).isEqualTo(1001);
        assertThat(node.path("message").asText()).contains("handoverNo");
    }

    @Test
    void 合法发布后出现在列表且库中已写入() throws Exception {
        String payload = """
                {"type":"LOST","title":"黑色的雨伞","category":"雨伞","location":"体育馆/操场",
                 "locationDetail":"看台东侧","eventTime":"2026-10-14T08:30:00","description":"伞骨有一处补丁"}
                """;

        ResponseEntity<String> created = postJson("/api/reports", payload);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detail = objectMapper.readTree(created.getBody()).path("data");
        assertThat(detail.path("type").asText()).isEqualTo("LOST");
        assertThat(detail.path("status").asText()).isEqualTo("OPEN");
        assertThat(detail.path("location").asText()).isEqualTo("体育馆/操场");
        assertThat(detail.path("publisher").path("nickname").asText()).isEqualTo("测试用户");
        long reportId = detail.path("id").asLong();

        JsonNode list = getReports().path("data");
        assertThat(list.path("total").asLong()).isEqualTo(1);
        assertThat(list.path("items").get(0).path("title").asText()).isEqualTo("黑色的雨伞");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT publisher_id FROM report WHERE id = ?", Long.class, reportId)).isEqualTo(publisherId);
    }

    @Test
    void 一次上传超过三张图片被拒() throws Exception {
        long reportId = createLostReportViaApi("多图测试");

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (int i = 0; i < 4; i++) {
            parts.add("files", new NamedByteArrayResource("image-" + i, "photo" + i + ".png"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/reports/" + reportId + "/images", HttpMethod.POST,
                new HttpEntity<>(parts, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode node = objectMapper.readTree(response.getBody());
        assertThat(node.path("code").asInt()).isEqualTo(1001);
        assertThat(node.path("message").asText()).contains("3");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_image WHERE report_id = ?", Integer.class, reportId)).isZero();
    }

    private ResponseEntity<String> postJson(String path, String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
    }

    private long createLostReportViaApi(String title) throws Exception {
        String payload = """
                {"type":"LOST","title":"%s","category":"其他","location":"图书馆",
                 "eventTime":"2026-10-14T08:30:00","description":"描述"}
                """.formatted(title);

        ResponseEntity<String> response = postJson("/api/reports", payload);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
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

    /** 带文件名的内存文件，用于构造 multipart 请求。 */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(String content, String filename) {
            super(content.getBytes(StandardCharsets.UTF_8));
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
