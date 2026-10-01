package metopa.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;


@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @BeforeEach
    void createUser() {
        try {
            identityService.createUser(
                    new CreateUserCommand(
                            "authuser",
                            "authuser@example.com",
                            "Auth User",
                            "StrongPassword123!"
                    )
            );
        } catch (UsernameAlreadyExistsException ignored) {
            // Usuário já existe no banco local de testes.
        }
    }

    @Test
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content()
                        .contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        ))
                .andExpect(jsonPath("$.title")
                        .value("Authentication required"))
                .andExpect(jsonPath("$.status")
                        .value(401))
                .andExpect(jsonPath("$.detail")
                        .value(
                                "Authentication is required to access this resource."
                        ));
    }

    @Test
    void shouldAuthenticateWithValidCredentials() throws Exception {
        mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user("authuser")
                                .password("StrongPassword123!")
                )
                .andExpect(status().isOk())
                .andExpect(authenticated()
                        .withUsername("authuser"));
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user("authuser")
                                .password("wrong-password")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated())
                .andExpect(content()
                        .contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        ))
                .andExpect(jsonPath("$.title")
                        .value("Authentication failed"))
                .andExpect(jsonPath("$.status")
                        .value(401))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid username or password."));
    }

    @Test
    void shouldKeepAuthenticationInSession() throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user("authuser")
                                .password("StrongPassword123!")
                )
                .andExpect(status().isOk())
                .andExpect(authenticated())
                .andReturn();

        var session = loginResult.getRequest().getSession(false);

        mockMvc.perform(
                        get("/api/auth/me")
                                .session(
                                        (org.springframework.mock.web.MockHttpSession) session
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username")
                        .value("authuser"));
    }

    @Test
    void shouldLogoutAndInvalidateAuthentication() throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        formLogin("/api/auth/login")
                                .user("authuser")
                                .password("StrongPassword123!")
                )
                .andExpect(status().isOk())
                .andExpect(authenticated())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }
}