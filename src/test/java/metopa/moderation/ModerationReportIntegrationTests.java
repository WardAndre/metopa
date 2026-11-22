
package metopa.moderation;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ModerationReportIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistReportForExistingWork() throws Exception {
        String username = "reporter-" +
                UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 8);

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Moderation Test User",
                        PASSWORD
                )
        );

        UUID reporterUserId = identityService
                .findByUsername(username)
                .id();

        UUID workId = catalogService.createWork(
                new CreateWorkCommand(
                        reporterUserId,
                        "Reported Work",
                        "Work created for moderation testing.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
        );

        String response = mockMvc.perform(
                        post("/api/works/{workId}/reports", workId)
                                .with(user(username))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "COPYRIGHT",
                                          "description": "Possible copyright infringement."
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String reportId = JsonPath.read(response, "$.id");

        entityManager.flush();

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                """
                SELECT
                    id,
                    work_id,
                    reporter_user_id,
                    reason,
                    description,
                    status,
                    created_at,
                    updated_at
                FROM moderation_report
                WHERE id = ?
                """,
                UUID.fromString(reportId)
        );

        assertThat(persisted.get("id"))
                .isEqualTo(UUID.fromString(reportId));

        assertThat(persisted.get("work_id"))
                .isEqualTo(workId);

        assertThat(persisted.get("reporter_user_id"))
                .isEqualTo(reporterUserId);

        assertThat(persisted.get("reason"))
                .isEqualTo(ReportReason.COPYRIGHT.name());

        assertThat(persisted.get("description"))
                .isEqualTo("Possible copyright infringement.");

        assertThat(persisted.get("status"))
                .isEqualTo(ReportStatus.OPEN.name());

        assertThat(persisted.get("created_at"))
                .isNotNull();

        assertThat(persisted.get("updated_at"))
                .isNotNull();
    }
}
