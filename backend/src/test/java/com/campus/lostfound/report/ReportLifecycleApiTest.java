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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工单 05：启事生命周期（编辑 / 删除 / 标记归还移交）的外部行为断言。
 * 沿用 ReportApiTest 的接缝：真实 HTTP + JdbcTemplate 夹具，只断言返回与库中数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportLifecycleApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @LocalServerPort
    private int port;

    /**
     * TestRestTemplate 底层的 HttpURLConnection 不支持 PATCH，这里单独用 JDK HttpClient
     * 发 PATCH；不发错误状态码时也不抛异常，交由断言检查状态码与响应体。
     */
    private RestTemplate patchClient;

    private long publisherId;
    private String token;
    private long otherId;
    private String otherToken;
    private long adminId;
    private String adminToken;

    @BeforeEach
    void resetDatabase() {
        patchClient = new RestTemplate(new JdkClientHttpRequestFactory());
        patchClient.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
            }
        });

        jdbcTemplate.execute("DELETE FROM report_image");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM app_user");
        publisherId = insertUser("2021001", "发布者", "USER");
        token = jwtService.generate(publisherId, UserRole.USER);
        otherId = insertUser("2021002", "路人甲", "USER");
        otherToken = jwtService.generate(otherId, UserRole.USER);
        adminId = insertUser("9000001", "管理员", "ADMIN");
        adminToken = jwtService.generate(adminId, UserRole.ADMIN);
    }

    @Test
    void 非发布者编辑被拒并返回越权码() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = patchJson("/api/reports/" + reportId,
                """
                {"title":"被篡改的标题"}
                """, otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1004);
        assertThat(titleOf(reportId)).isEqualTo("校园卡");
    }

    @Test
    void 非发布者删除被拒并返回越权码() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = delete("/api/reports/" + reportId, otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1004);
        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    void 超过三十分钟编辑被拒并给出可辨识错误码() throws Exception {
        long reportId = createLostReport("校园卡");
        backdateCreatedAt(reportId, LocalDateTime.now().minusMinutes(31));

        ResponseEntity<String> response = patchJson("/api/reports/" + reportId,
                """
                {"title":"太晚修改"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode node = objectMapper.readTree(response.getBody());
        assertThat(node.path("code").asInt()).isEqualTo(1005);
        assertThat(node.path("message").asText()).contains("30 分钟");
        assertThat(titleOf(reportId)).isEqualTo("校园卡");
    }

    @Test
    void 超过三十分钟删除被拒() throws Exception {
        long reportId = createLostReport("校园卡");
        backdateCreatedAt(reportId, LocalDateTime.now().minusMinutes(31));

        ResponseEntity<String> response = delete("/api/reports/" + reportId, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1005);
        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    void 发布者可在时间窗内编辑并写库() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = patchJson("/api/reports/" + reportId,
                """
                {"title":"修改后的校园卡","category":"钥匙","location":"食堂","locationDetail":"二楼"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("title").asText()).isEqualTo("修改后的校园卡");
        assertThat(data.path("category").asText()).isEqualTo("钥匙");
        assertThat(titleOf(reportId)).isEqualTo("修改后的校园卡");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT location FROM report WHERE id = ?", String.class, reportId)).isEqualTo("食堂");
    }

    @Test
    void 编辑时非法分类被拒() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = patchJson("/api/reports/" + reportId,
                """
                {"category":"不存在的分类"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1001);
        assertThat(categoryOf(reportId)).isEqualTo("其他");
    }

    @Test
    void 非发布者标记状态被拒() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = putJson("/api/reports/" + reportId + "/status",
                """
                {"status":"RETURNED"}
                """, otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1004);
        assertThat(statusOf(reportId)).isEqualTo("OPEN");
    }

    @Test
    void 标记归还后状态变更并可查() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = putJson("/api/reports/" + reportId + "/status",
                """
                {"status":"RETURNED"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()).path("data").path("status").asText())
                .isEqualTo("RETURNED");
        assertThat(getReportDetail(reportId).path("data").path("status").asText()).isEqualTo("RETURNED");
        assertThat(statusOf(reportId)).isEqualTo("RETURNED");
    }

    @Test
    void 标记已归还在默认列表消失而includeClosed可见() throws Exception {
        long reportId = createLostReport("校园卡");
        putJson("/api/reports/" + reportId + "/status", """
                {"status":"RETURNED"}
                """, token);

        JsonNode defaults = getReports("").path("data");
        assertThat(defaults.path("total").asLong()).isZero();

        JsonNode withClosed = getReports("?includeClosed=true").path("data");
        assertThat(withClosed.path("total").asLong()).isEqualTo(1);
    }

    @Test
    void 失物启事标记已移交被拒() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = putJson("/api/reports/" + reportId + "/status",
                """
                {"status":"HANDED_OVER","handoverNo":"HY-001"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1001);
        assertThat(statusOf(reportId)).isEqualTo("OPEN");
    }

    @Test
    void 招领启事标记已移交缺少移交单号被拒() throws Exception {
        long reportId = createFoundReport("捡到耳机");

        ResponseEntity<String> response = putJson("/api/reports/" + reportId + "/status",
                """
                {"status":"HANDED_OVER"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt()).isEqualTo(1001);
        assertThat(statusOf(reportId)).isEqualTo("OPEN");
    }

    @Test
    void 招领启事标记已移交成功写库() throws Exception {
        long reportId = createFoundReport("捡到耳机");

        ResponseEntity<String> response = putJson("/api/reports/" + reportId + "/status",
                """
                {"status":"HANDED_OVER","handoverNo":"HY-2026-001"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(reportId)).isEqualTo("HANDED_OVER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT handover_no FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("HY-2026-001");
    }

    @Test
    void 标记状态不受三十分钟编辑窗口限制() throws Exception {
        long reportId = createLostReport("校园卡");
        backdateCreatedAt(reportId, LocalDateTime.now().minusHours(5));

        ResponseEntity<String> response = putJson("/api/reports/" + reportId + "/status",
                """
                {"status":"RETURNED"}
                """, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(reportId)).isEqualTo("RETURNED");
    }

    @Test
    void 管理员可编辑他人启事() throws Exception {
        long reportId = createLostReport("校园卡");

        ResponseEntity<String> response = patchJson("/api/reports/" + reportId,
                """
                {"title":"管理员修改"}
                """, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(titleOf(reportId)).isEqualTo("管理员修改");
    }

    @Test
    void 我的发布返回当前用户全部启事且不含他人() throws Exception {
        createLostReport("我的第一条");
        long returned = createLostReport("我的第二条");
        putJson("/api/reports/" + returned + "/status", """
                {"status":"RETURNED"}
                """, token);
        createReport(otherToken, foundPayload("别人的启事"));

        JsonNode data = getMyReports(token).path("data");

        assertThat(data.path("total").asLong()).isEqualTo(2);
        List<String> titles = new ArrayList<>();
        data.path("items").forEach(item -> titles.add(item.path("title").asText()));
        assertThat(titles).containsExactlyInAnyOrder("我的第一条", "我的第二条");
    }

    @Test
    void 我的发布未认证被拒() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/me/reports", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- 请求辅助 ----------

    private ResponseEntity<String> patchJson(String path, String payload, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearer);
        return patchClient.exchange("http://localhost:" + port + path, HttpMethod.PATCH,
                new HttpEntity<>(payload, headers), String.class);
    }

    private ResponseEntity<String> putJson(String path, String payload, String bearer) {
        return exchangeJson(HttpMethod.PUT, path, payload, bearer);
    }

    private ResponseEntity<String> delete(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String path, String payload, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearer);
        return restTemplate.exchange(path, method, new HttpEntity<>(payload, headers), String.class);
    }

    private JsonNode getMyReports(String bearer) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        String raw = restTemplate.exchange(
                "/api/me/reports", HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        return objectMapper.readTree(raw);
    }

    private JsonNode getReports(String query) throws Exception {
        return objectMapper.readTree(restTemplate.getForObject("/api/reports" + query, String.class));
    }

    private JsonNode getReportDetail(long reportId) throws Exception {
        return objectMapper.readTree(restTemplate.getForObject("/api/reports/" + reportId, String.class));
    }

    private long createLostReport(String title) throws Exception {
        return createReport(token, """
                {"type":"LOST","title":"%s","category":"其他","location":"图书馆",
                 "eventTime":"2026-10-14T08:30:00","description":"描述"}
                """.formatted(title));
    }

    private long createFoundReport(String title) throws Exception {
        return createReport(token, foundPayload(title));
    }

    private String foundPayload(String title) {
        return """
                {"type":"FOUND","title":"%s","category":"耳机","location":"图书馆",
                 "eventTime":"2026-10-14T08:30:00","description":"描述","custody":"SELF"}
                """.formatted(title);
    }

    private long createReport(String bearer, String payload) throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, "/api/reports", payload, bearer);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
    }

    // ---------- 库中数据辅助 ----------

    private void backdateCreatedAt(long reportId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE report SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), reportId);
    }

    private String titleOf(long reportId) {
        return jdbcTemplate.queryForObject("SELECT title FROM report WHERE id = ?", String.class, reportId);
    }

    private String categoryOf(long reportId) {
        return jdbcTemplate.queryForObject("SELECT category FROM report WHERE id = ?", String.class, reportId);
    }

    private String statusOf(long reportId) {
        return jdbcTemplate.queryForObject("SELECT status FROM report WHERE id = ?", String.class, reportId);
    }

    private int reportCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report", Integer.class);
    }

    private long insertUser(String studentNo, String nickname, String role) {
        jdbcTemplate.update("""
                INSERT INTO app_user (student_no, nickname, password_hash, role, status, contact_public, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 0, NOW(6), NOW(6))
                """, studentNo, nickname, "$2a$10$abcdefghijklmnopqrstuv", role);
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE student_no = ?", Long.class, studentNo);
    }
}
