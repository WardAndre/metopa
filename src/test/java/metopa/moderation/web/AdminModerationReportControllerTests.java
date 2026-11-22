package metopa.moderation.web;

import metopa.moderation.ModerationReportSummary;
import metopa.moderation.ModerationService;
import metopa.moderation.ReportReason;
import metopa.moderation.ReportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationReportControllerTests {

    private static final String ENDPOINT =
            "/api/admin/moderation/reports";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerationService moderationService;

    @Test
    void shouldReturnPaginatedReportsWithDefaultParameters()
            throws Exception {

        UUID reportId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        ModerationReportSummary report =
                new ModerationReportSummary(
                        reportId,
                        workId,
                        ReportReason.COPYRIGHT,
                        "Possible copyright infringement.",
                        ReportStatus.OPEN,
                        Instant.parse("2026-11-11T02:30:00Z")
                );

        when(moderationService.findReports(null, 0, 20))
                .thenReturn(
                        new PageImpl<>(
                                List.of(report),
                                PageRequest.of(0, 20),
                                1
                        )
                );

        mockMvc.perform(get(ENDPOINT)
                        .with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(reportId.toString()))
                .andExpect(jsonPath("$.content[0].workId")
                        .value(workId.toString()))
                .andExpect(jsonPath("$.content[0].reason")
                        .value("COPYRIGHT"))
                .andExpect(jsonPath("$.content[0].status")
                        .value("OPEN"));

        verify(moderationService).findReports(null, 0, 20);
    }

    @Test
    void shouldFilterReportsByStatusWithPagination()
            throws Exception {

        ModerationReportSummary report =
                new ModerationReportSummary(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ReportReason.AGE_RATING,
                        "Incorrect age classification.",
                        ReportStatus.UNDER_REVIEW,
                        Instant.parse("2026-11-11T02:30:00Z")
                );

        when(moderationService.findReports(
                ReportStatus.UNDER_REVIEW,
                1,
                10
        )).thenReturn(
                new PageImpl<>(
                        List.of(report),
                        PageRequest.of(1, 10),
                        11
                )
        );

        mockMvc.perform(get(ENDPOINT)
                        .with(user("admin").roles("USER", "ADMIN"))
                        .param("status", "UNDER_REVIEW")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].status")
                        .value("UNDER_REVIEW"));

        verify(moderationService).findReports(
                ReportStatus.UNDER_REVIEW,
                1,
                10
        );
    }

    @Test
    void shouldRejectUnauthenticatedReportListing()
            throws Exception {

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(moderationService);
    }

    @Test
    void shouldRejectRegularUserFromReportListing()
            throws Exception {

        mockMvc.perform(get(ENDPOINT)
                        .with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(moderationService);
    }

    @Test
    void shouldRejectNegativePage() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51"})
    void shouldRejectInvalidPageSize(String size)
            throws Exception {

        mockMvc.perform(get(ENDPOINT)
                        .with(user("admin").roles("ADMIN"))
                        .param("size", size))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(moderationService);
    }

    @Test
    void shouldRejectInvalidReportStatus() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(user("admin").roles("ADMIN"))
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(moderationService);
    }
}
