package metopa.moderation.web;

import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.moderation.ModerationService;
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
class AdminModerationResolveIntegrationTests {

    private static final String PASSWORD = "StrongPassword123!";

    private static final String START_REVIEW_BODY = """
            {
              "note": "Administrative review initiated."
            }
            """;

    private static final String RESOLVE_BODY = """
            {
              "decision": "NO_VIOLATION",
              "note": "No copyright violation was found."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private ModerationReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createdReportId;
    private UUID createdUserId;
    private UUID createdWorkId;
    
    @AfterEach
    void cleanUp() {
        if (createdReportId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM moderation_audit_entry
                    WHERE report_id = ?
                    """,
                    createdReportId
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM moderation_report
                    WHERE id = ?
                    """,
                    createdReportId
            );
        }

        if (createdWorkId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM work
                    WHERE id = ?
                    """,
                    createdWorkId
            );
        }

        if (createdUserId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM user_account
                    WHERE id = ?
                    """,
                    createdUserId
            );
        }
    }

    @Test
    void shouldResolveReportUsingRealAdminSession()
            throws Exception {

        UUID reportId = createReport();
        String username = createUser(true);

        MockHttpSession session = login(username);

        startReview(reportId, session);

        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESOLVE_BODY)
                )
                .andExpect(status().isNoContent());

        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("NO_VIOLATION");

        assertThat(countAuditEntries(reportId))
                .isEqualTo(2L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isEqualTo(1L);

        UUID resolvingAdminId = jdbcTemplate.queryForObject(
                """
                SELECT admin_user_id
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'REPORT_RESOLVED'
                """,
                UUID.class,
                reportId
        );

        assertThat(resolvingAdminId)
                .isEqualTo(createdUserId);

        String resolutionNote = jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'REPORT_RESOLVED'
                """,
                String.class,
                reportId
        );

        assertThat(resolutionNote)
                .isEqualTo("No copyright violation was found.");
    }

    @Test
    void shouldRejectDuplicateResolutionWithRealAdminSession()
            throws Exception {

        UUID reportId = createReport();
        String username = createUser(true);

        MockHttpSession session = login(username);

        startReview(reportId, session);

        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESOLVE_BODY)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESOLVE_BODY)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("NO_VIOLATION");

        assertThat(countAuditEntries(reportId))
                .isEqualTo(2L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isEqualTo(1L);
    }

    @Test
    void shouldRejectRegularUserFromResolvingReport()
            throws Exception {

        UUID reportId = createReport();

        moderationService.startReview(
                reportId,
                UUID.randomUUID(),
                "Administrative review initiated."
        );

        String username = createUser(false);

        MockHttpSession session = login(username);

        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESOLVE_BODY)
                )
                .andExpect(status().isForbidden());

        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        assertThat(findDecision(reportId))
                .isNull();

        assertThat(countAuditEntries(reportId))
                .isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isZero();
    }

    @Test
    void shouldRestrictWorkWhenResolvingReportWithRealAdminSession()
            throws Exception {

        String username = createUser(true);

        UUID workId = catalogService.createWork(
                new CreateWorkCommand(
                        createdUserId,
                        "Reported Work " + UUID.randomUUID(),
                        "Work reported for a copyright violation.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
        );

        createdWorkId = workId;

        ModerationReport report = new ModerationReport(
                workId,
                createdUserId,
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        UUID reportId = report.getId();
        createdReportId = reportId;

        MockHttpSession session = login(username);

        // The work must be public before moderation.
        assertThat(catalogService.hasPublicVisibility(workId))
                .isTrue();

        startReview(reportId, session);

        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        // Resolve the report with a work restriction decision.
        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "decision": "WORK_RESTRICTED",
                                      "note": "Confirmed content policy violation."
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        // Verify the persisted moderation decision.
        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("WORK_RESTRICTED");

        // Verify the persisted work visibility.
        String visibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                workId
        );

        assertThat(visibility)
                .isEqualTo("RESTRICTED");

        assertThat(catalogService.hasPublicVisibility(workId))
                .isFalse();

        // Verify the moderation audit trail.
        assertThat(countAuditEntries(reportId))
                .isEqualTo(2L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isEqualTo(1L);

        UUID resolvingAdminId = jdbcTemplate.queryForObject(
                """
                SELECT admin_user_id
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'REPORT_RESOLVED'
                """,
                UUID.class,
                reportId
        );

        assertThat(resolvingAdminId)
                .isEqualTo(createdUserId);

        String resolutionNote = jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'REPORT_RESOLVED'
                """,
                String.class,
                reportId
        );

        assertThat(resolutionNote)
                .isEqualTo("Confirmed content policy violation.");
    }

    @Test
    void shouldRollbackResolutionWhenWorkIsAlreadyRestricted()
            throws Exception {

        String username = createUser(true);

        UUID workId = catalogService.createWork(
                new CreateWorkCommand(
                        createdUserId,
                        "Already Restricted Work " + UUID.randomUUID(),
                        "Work previously restricted by moderation.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
        );

        createdWorkId = workId;

        ModerationReport report = new ModerationReport(
                workId,
                createdUserId,
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        UUID reportId = report.getId();
        createdReportId = reportId;

        // Restrict the work before resolving the report.
        catalogService.restrictWork(workId);

        assertThat(catalogService.hasPublicVisibility(workId))
                .isFalse();

        MockHttpSession session = login(username);

        startReview(reportId, session);

        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        // A repeated restriction must fail.
        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "decision": "WORK_RESTRICTED",
                                      "note": "Confirmed content policy violation."
                                    }
                                    """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // The report must remain under review.
        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        assertThat(findDecision(reportId))
                .isNull();

        // No resolution audit entry must be persisted.
        assertThat(countAuditEntries(reportId))
                .isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isZero();

        // The work must remain restricted.
        String visibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                workId
        );

        assertThat(visibility)
                .isEqualTo("RESTRICTED");
    }

    @Test
    void shouldRestoreRestrictedWorkUsingRealAdminSession()
            throws Exception {

        String username = createUser(true);

        UUID workId = catalogService.createWork(
                new CreateWorkCommand(
                        createdUserId,
                        "Restored Work " + UUID.randomUUID(),
                        "Work restored after administrative review.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
        );

        createdWorkId = workId;

        ModerationReport report = new ModerationReport(
                workId,
                createdUserId,
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        UUID reportId = report.getId();
        createdReportId = reportId;

        MockHttpSession session = login(username);

        // The work must initially be public.
        assertThat(catalogService.hasPublicVisibility(workId))
                .isTrue();

        // Restrict the work through the administrative API.
        startReview(reportId, session);

        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "decision": "WORK_RESTRICTED",
                                      "note": "Confirmed content policy violation."
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        assertThat(catalogService.hasPublicVisibility(workId))
                .isFalse();

        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("WORK_RESTRICTED");

        // Restore the restricted work.
        mockMvc.perform(
                        post(
                                "/api/admin/moderation/reports/{reportId}/restore",
                                reportId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "note": "Restriction reversed after administrative review."
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        // Verify the persisted work visibility.
        String visibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                workId
        );

        assertThat(visibility)
                .isEqualTo("PUBLIC");

        assertThat(catalogService.hasPublicVisibility(workId))
                .isTrue();

        // Preserve the original moderation decision.
        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("WORK_RESTRICTED");

        // Verify the complete audit trail.
        assertThat(countAuditEntries(reportId))
                .isEqualTo(3L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "WORK_RESTORED"
        )).isEqualTo(1L);

        UUID restoringAdminId = jdbcTemplate.queryForObject(
                """
                SELECT admin_user_id
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'WORK_RESTORED'
                """,
                UUID.class,
                reportId
        );

        assertThat(restoringAdminId)
                .isEqualTo(createdUserId);

        String restorationNote = jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'WORK_RESTORED'
                """,
                String.class,
                reportId
        );

        assertThat(restorationNote)
                .isEqualTo(
                        "Restriction reversed after administrative review."
                );

        // A second restoration attempt must be rejected.
        mockMvc.perform(
                        post(
                                "/api/admin/moderation/reports/{reportId}/restore",
                                reportId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "note": "Duplicate restoration attempt."
                                    }
                                    """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // The work must remain public after the failed attempt.
        String visibilityAfterDuplicateAttempt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT visibility
                        FROM work
                        WHERE id = ?
                        """,
                        String.class,
                        workId
                );

        assertThat(visibilityAfterDuplicateAttempt)
                .isEqualTo("PUBLIC");

        assertThat(catalogService.hasPublicVisibility(workId))
                .isTrue();

        // The original moderation resolution must remain unchanged.
        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("WORK_RESTRICTED");

        // The failed attempt must not create additional audit entries.
        assertThat(countAuditEntries(reportId))
                .isEqualTo(3L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "WORK_RESTORED"
        )).isEqualTo(1L);
    }

    @Test
    void shouldRejectRestorationWhenReportHasNoViolation()
            throws Exception {

        UUID reportId = createReport();
        String username = createUser(true);

        MockHttpSession session = login(username);

        startReview(reportId, session);

        // Resolve the report without applying a restriction.
        mockMvc.perform(
                        post(resolvePath(reportId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESOLVE_BODY)
                )
                .andExpect(status().isNoContent());

        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("NO_VIOLATION");

        assertThat(countAuditEntries(reportId))
                .isEqualTo(2L);

        // Restoration must be rejected for a NO_VIOLATION decision.
        mockMvc.perform(
                        post(
                                "/api/admin/moderation/reports/{reportId}/restore",
                                reportId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "note": "Invalid restoration attempt."
                                    }
                                    """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // The original resolution must remain unchanged.
        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("NO_VIOLATION");

        // The rejected restoration must not create an audit entry.
        assertThat(countAuditEntries(reportId))
                .isEqualTo(2L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REPORT_RESOLVED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "WORK_RESTORED"
        )).isZero();
    }

    @Test
    void shouldRejectRegularUserFromRestoringWork()
            throws Exception {

        UUID reportId = createReport();

        moderationService.startReview(
                reportId,
                UUID.randomUUID(),
                "Administrative review initiated."
        );

        String username = createUser(false);

        MockHttpSession session = login(username);

        // A regular user must not access the restoration endpoint.
        mockMvc.perform(
                        post(
                                "/api/admin/moderation/reports/{reportId}/restore",
                                reportId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "note": "Unauthorized restoration attempt."
                                    }
                                    """)
                )
                .andExpect(status().isForbidden());

        // The report must remain unchanged.
        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        assertThat(findDecision(reportId))
                .isNull();

        // The rejected request must not create an audit entry.
        assertThat(countAuditEntries(reportId))
                .isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "REVIEW_STARTED"
        )).isEqualTo(1L);

        assertThat(countAuditEntries(
                reportId,
                "WORK_RESTORED"
        )).isZero();
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

        String username =
                (admin ? "admin-" : "reader-") + suffix;

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
                                .param("password", PASSWORD)
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();

        return session;
    }

    private void startReview(
            UUID reportId,
            MockHttpSession session
    ) throws Exception {

        mockMvc.perform(
                        post(
                                "/api/admin/moderation/reports/{reportId}/start-review",
                                reportId
                        )
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(START_REVIEW_BODY)
                )
                .andExpect(status().isNoContent());
    }

    private static String resolvePath(UUID reportId) {
        return "/api/admin/moderation/reports/"
                + reportId
                + "/resolve";
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

    private String findDecision(UUID reportId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT decision
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

    private long countAuditEntries(
            UUID reportId,
            String action
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = ?
                """,
                Long.class,
                reportId,
                action
        );

        return count;
    }
}
