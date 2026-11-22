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
class AdminModerationStartReviewControllerTests {

    private static final String BASE_PATH =
            "/api/admin/moderation/reports/";

    private static final String VALID_BODY = """
            {
              "note": "Copyright review initiated."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerationService moderationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldStartReviewAsAdministrator() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID adminUserId = stubAdmin();

        mockMvc.perform(post(path(reportId))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNoContent());

        verify(identityService).findByUsername("admin");

        verify(moderationService).startReview(
                reportId,
                adminUserId,
                "Copyright review initiated."
        );
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectRegularUser() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("reader").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectAdminRequestWithoutCsrf() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectBlankNote() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": "   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityService, moderationService);
    }

    @Test
    void shouldRejectNoteLongerThan2000Characters()
            throws Exception {

        String body = "{\"note\":\""
                + "a".repeat(2001)
                + "\"}";

        mockMvc.perform(post(path(UUID.randomUUID()))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
        )).when(moderationService).startReview(
                reportId,
                adminUserId,
                "Copyright review initiated."
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
    void shouldReturnConflictForInvalidTransition()
            throws Exception {

        UUID reportId = UUID.randomUUID();
        UUID adminUserId = stubAdmin();

        doThrow(new IllegalStateException(
                "Only open reports can enter review."
        )).when(moderationService).startReview(
                reportId,
                adminUserId,
                "Copyright review initiated."
        );

        mockMvc.perform(post(path(reportId))
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
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
        return BASE_PATH + reportId + "/start-review";
    }
}
