package metopa.moderation.web;

import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.moderation.ReportReason;
import metopa.moderation.internal.ModerationReport;
import metopa.moderation.internal.ModerationReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationStartReviewIntegrationTests {

    private static final String PASSWORD = "StrongPassword123!";

    private static final String REQUEST_BODY = """
            {
              "note": "Copyright review initiated."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private ModerationReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createdReportId;
    private UUID createdUserId;

    @AfterEach
    void cleanUp() {
        if (createdReportId != null) {
            jdbcTemplate.update(
                    "DELETE FROM moderation_audit_entry WHERE report_id = ?",
                    createdReportId
            );

            jdbcTemplate.update(
                    "DELETE FROM moderation_report WHERE id = ?",
                    createdReportId
            );
        }

        if (createdUserId != null) {
            jdbcTemplate.update(
                    "DELETE FROM user_account WHERE id = ?",
                    createdUserId
            );
        }
    }

    @Test
    void shouldStartReviewWithRealAdminSession()
            throws Exception {

        UUID reportId = createReport();
        String username = createUser(true);
        MockHttpSession session = login(username);

        mockMvc.perform(startReviewRequest(reportId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isNoContent());

        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        assertThat(countAuditEntries(reportId))
                .isEqualTo(1L);

        UUID recordedAdminId = jdbcTemplate.queryForObject(
                """
                SELECT admin_user_id
                FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                UUID.class,
                reportId
        );

        assertThat(recordedAdminId)
                .isEqualTo(createdUserId);

        String action = jdbcTemplate.queryForObject(
                """
                SELECT action
                FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                String.class,
                reportId
        );

        assertThat(action).isEqualTo("REVIEW_STARTED");
    }

    @Test
    void shouldRejectDuplicateReviewWithRealAdminSession()
            throws Exception {

        UUID reportId = createReport();
        String username = createUser(true);
        MockHttpSession session = login(username);

        mockMvc.perform(startReviewRequest(reportId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(startReviewRequest(reportId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        assertThat(countAuditEntries(reportId))
                .isEqualTo(1L);
    }

    @Test
    void shouldRejectRealRegularUserFromStartingReview()
            throws Exception {

        UUID reportId = createReport();
        String username = createUser(false);
        MockHttpSession session = login(username);

        mockMvc.perform(startReviewRequest(reportId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isForbidden());

        assertThat(findStatus(reportId))
                .isEqualTo("OPEN");

        assertThat(countAuditEntries(reportId))
                .isZero();
    }

    private UUID createReport() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        createdReportId = report.getId();

        return createdReportId;
    }

    private String createUser(boolean admin) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        String username = (admin ? "admin-" : "reader-") + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Moderation Integration Test",
                        PASSWORD
                )
        );

        createdUserId = identityService
                .findByUsername(username)
                .id();

        if (admin) {
            int updatedRows = jdbcTemplate.update(
                    """
                    UPDATE user_account
                    SET role = 'ADMIN'
                    WHERE id = ?
                    """,
                    createdUserId
            );

            assertThat(updatedRows).isEqualTo(1);
        }

        return username;
    }

    private MockHttpSession login(String username)
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .param("username", username)
                                .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();

        return session;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    startReviewRequest(UUID reportId) {
        return post(
                "/api/admin/moderation/reports/{reportId}/start-review",
                reportId
        );
    }

    private String findStatus(UUID reportId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM moderation_report
                WHERE id = ?
                """,
                String.class,
                reportId
        );
    }

    private long countAuditEntries(UUID reportId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                Long.class,
                reportId
        );

        return count;
    }
}
