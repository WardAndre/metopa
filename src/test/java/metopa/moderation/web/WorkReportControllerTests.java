package metopa.moderation.web;

import metopa.catalog.WorkNotFoundException;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.moderation.CreateReportCommand;
import metopa.moderation.ModerationService;
import metopa.moderation.ReportReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerationService moderationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldCreateReportForAuthenticatedUser() throws Exception {
        UUID workId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();

        UserReference userReference = mock(UserReference.class);

        when(userReference.id()).thenReturn(reporterId);

        when(identityService.findByUsername("reader"))
                .thenReturn(userReference);

        when(moderationService.createReport(any(CreateReportCommand.class)))
                .thenReturn(reportId);

        mockMvc.perform(post("/api/works/{workId}/reports", workId)
                        .with(user("reader"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "COPYRIGHT",
                                  "description": "Possible copyright infringement."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reportId.toString()));

        verify(moderationService).createReport(argThat(command ->
                reporterId.equals(command.reporterUserId())
                        && workId.equals(command.workId())
                        && command.reason() == ReportReason.COPYRIGHT
                        && "Possible copyright infringement."
                        .equals(command.description())
        ));
    }

    @Test
    void shouldRejectUnauthenticatedReportCreation() throws Exception {
        mockMvc.perform(post(
                        "/api/works/{workId}/reports",
                        UUID.randomUUID()
                )
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "COPYRIGHT",
                                  "description": "Possible copyright infringement."
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(moderationService);
    }

    @Test
    void shouldRequireCsrfForReportCreation() throws Exception {
        mockMvc.perform(post(
                        "/api/works/{workId}/reports",
                        UUID.randomUUID()
                )
                        .with(user("reader"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "COPYRIGHT",
                                  "description": "Possible copyright infringement."
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(moderationService);
    }

    @Test
    void shouldRejectInvalidReportData() throws Exception {
        mockMvc.perform(post(
                        "/api/works/{workId}/reports",
                        UUID.randomUUID()
                )
                        .with(user("reader"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "OTHER",
                                  "description": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid report data"));

        verifyNoInteractions(moderationService);
    }

    @Test
    void shouldReturnNotFoundForNonexistentWork() throws Exception {
        UUID workId = UUID.randomUUID();

        UserReference userReference = mock(UserReference.class);

        when(userReference.id()).thenReturn(UUID.randomUUID());

        when(identityService.findByUsername("reader"))
                .thenReturn(userReference);

        when(moderationService.createReport(any(CreateReportCommand.class)))
                .thenThrow(new WorkNotFoundException(workId));

        mockMvc.perform(post("/api/works/{workId}/reports", workId)
                        .with(user("reader"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "COPYRIGHT",
                                  "description": "Possible copyright infringement."
                                }
                                """))
                .andExpect(status().isNotFound());

        verify(moderationService).createReport(any(CreateReportCommand.class));
    }
}