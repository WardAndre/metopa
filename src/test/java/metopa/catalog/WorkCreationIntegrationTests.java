package metopa.catalog;

import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UsernameAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
class WorkCreationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureAuthorExists() {
        try {
            identityService.createUser(
                    new CreateUserCommand(
                            "catalogauthor",
                            "catalogauthor@example.com",
                            "Catalog Author",
                            "StrongPassword123!"
                    )
            );
        } catch (UsernameAlreadyExistsException ignored) {
            // User already created by a previous test execution.
        }
    }

    @Test
    void shouldCreateWorkForAuthenticatedUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user("catalogauthor")
                                .password("StrongPassword123!")
                )
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("catalogauthor"))
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        String title = "Integration Work " + UUID.randomUUID();

        mockMvc.perform(
                        post("/api/works")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "  %s  ",
                                          "description": "  Created through the complete HTTP flow.  ",
                                          "type": "GRAPHIC_NOVEL",
                                          "readingDirection": "LEFT_TO_RIGHT",
                                          "presentationMode": "SINGLE_PAGE"
                                        }
                                        """.formatted(title))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        UUID storedOwnerId = jdbcTemplate.queryForObject(
                """
                SELECT owner_id
                FROM work
                WHERE title = ?
                """,
                UUID.class,
                title
        );

        UUID authenticatedUserId =
                identityService
                        .findByUsername("catalogauthor")
                        .id();

        assertThat(storedOwnerId)
                .isEqualTo(authenticatedUserId);
    }

    @Test
    void shouldRejectInvalidWorkData() throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user("catalogauthor")
                                .password("StrongPassword123!")
                )
                .andExpect(status().isOk())
                .andExpect(authenticated())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(
                        post("/api/works")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "title": "   ",
                                      "description": "Invalid work",
                                      "type": "COMIC",
                                      "readingDirection": "LEFT_TO_RIGHT",
                                      "presentationMode": "SINGLE_PAGE"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Invalid work data"))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.detail")
                        .value(
                                "One or more work fields are invalid."
                        ));
    }
}