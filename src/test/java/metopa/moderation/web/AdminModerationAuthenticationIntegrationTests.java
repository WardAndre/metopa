package metopa.moderation.web;

import jakarta.persistence.EntityManager;
import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminModerationAuthenticationIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

    private static final String ADMIN_ENDPOINT =
            "/api/admin/moderation/reports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldRejectAuthenticatedRegularUserFromAdminEndpoint()
            throws Exception {

        String username = createUser("reader");

        MockHttpSession session = login(username);

        mockMvc.perform(get(ADMIN_ENDPOINT)
                        .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAuthenticatedAdminFromDatabase()
            throws Exception {

        String username = createUser("admin");

        UUID userId = identityService
                .findByUsername(username)
                .id();

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE user_account
                SET role = 'ADMIN'
                WHERE id = ?
                """,
                userId
        );

        assertThat(updatedRows).isEqualTo(1);

        entityManager.clear();

        MockHttpSession session = login(username);

        mockMvc.perform(get(ADMIN_ENDPOINT)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    private String createUser(String prefix) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        String username = prefix + "-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Moderation Test User",
                        PASSWORD
                )
        );

        entityManager.flush();

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
}
