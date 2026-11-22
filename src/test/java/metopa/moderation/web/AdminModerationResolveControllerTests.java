package metopa.moderation.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.moderation.ModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationResolveControllerTests {

    private static final String BASE_PATH =
            "/api/admin/moderation/reports/";

    private static final String VALID_NOTE =
            "No copyright violation was found.";

    private static final String VALID_BODY = """
            {
              "decision": "NO_VIOLATION",
              "note": "No copyright violation was found."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerationService moderationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldResolveReportAsAdministrator() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID adminUserId = stubAdmin();

        mockMvc.perform(post(path(reportId))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNoContent());

        verify(identityService).findByUsername("admin");

        verify(moderationService).resolveNoViolation(
                reportId,
                adminUserId,
                VALID_NOTE
        );
    }

    @Test
    void shouldRejectUnauthenticatedResolution()
            throws Exception {

        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectRegularUserFromResolvingReport()
            throws Exception {

        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("reader").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectAdminResolutionWithoutCsrf()
            throws Exception {

        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectBlankResolutionNote() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "NO_VIOLATION",
                                  "note": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectResolutionNoteLongerThan2000Characters()
            throws Exception {

        String body = """
                {
                  "decision": "NO_VIOLATION",
                  "note": "%s"
                }
                """.formatted("a".repeat(2001));

        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectMissingDecision() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "No violation was found."
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectUnsupportedDecision() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "decision": "INVALID_DECISION",
                              "note": "Restriction requested."
                            }
                            """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldReturnNotFoundForMissingReport()
            throws Exception {

        UUID reportId = UUID.randomUUID();
        UUID adminUserId = stubAdmin();

        doThrow(new NoSuchElementException(
                "Moderation report not found."
        )).when(moderationService).resolveNoViolation(
                reportId,
                adminUserId,
                VALID_NOTE
        );

        mockMvc.perform(post(path(reportId))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnConflictForInvalidResolution()
            throws Exception {

        UUID reportId = UUID.randomUUID();
        UUID adminUserId = stubAdmin();

        doThrow(new IllegalStateException(
                "Only reports under review can be resolved."
        )).when(moderationService).resolveNoViolation(
                reportId,
                adminUserId,
                VALID_NOTE
        );

        mockMvc.perform(post(path(reportId))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldAcceptWorkRestrictedDecision() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        String note = "Confirmed content policy violation.";

        UserReference admin = mock(UserReference.class);

        when(admin.id()).thenReturn(adminUserId);

        when(identityService.findByUsername("admin"))
                .thenReturn(admin);

        mockMvc.perform(
                post(
                        "/api/admin/moderation/reports/{reportId}/resolve",
                        reportId
                )
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "decision": "WORK_RESTRICTED",
                              "note": "Confirmed content policy violation."
                            }
                            """)
        ).andExpect(status().isNoContent());

        verify(identityService)
                .findByUsername("admin");

        verify(moderationService)
                .resolveWorkRestriction(
                        reportId,
                        adminUserId,
                        note
                );

        verify(moderationService, never())
                .resolveNoViolation(
                        any(UUID.class),
                        any(UUID.class),
                        any(String.class)
                );
    }

    @Test
    void shouldRestoreRestrictedWork() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        UserReference admin = mock(UserReference.class);

        when(admin.id()).thenReturn(adminUserId);
        when(identityService.findByUsername("admin"))
                .thenReturn(admin);

        mockMvc.perform(
                        post(
                                "/api/admin/moderation/reports/{reportId}/restore",
                                reportId
                        )
                                .with(user("admin").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "note": "Restriction reversed after administrative review."
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        verify(identityService).findByUsername("admin");

        verify(moderationService).restoreWork(
                reportId,
                adminUserId,
                "Restriction reversed after administrative review."
        );
    }

    private UUID stubAdmin() {
        UUID adminUserId = UUID.randomUUID();

        when(identityService.findByUsername("admin"))
                .thenReturn(new UserReference(
                        adminUserId,
                        "admin"
                ));

        return adminUserId;
    }

    private static String path(UUID reportId) {
        return BASE_PATH + reportId + "/resolve";
    }
}
