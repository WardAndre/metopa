package metopa.identity.web;

import jakarta.validation.ConstraintViolationException;
import metopa.identity.CreateUserCommand;
import metopa.identity.EmailAlreadyExistsException;
import metopa.identity.IdentityService;
import metopa.identity.UsernameAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(IdentityExceptionHandler.class)
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldCreateUser() throws Exception {
        UUID userId =
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        when(identityService.createUser(any(CreateUserCommand.class)))
                .thenReturn(userId);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "  Andre  ",
                                  "email": "  Andre@Example.com  ",
                                  "displayName": "  André Ward  ",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id")
                        .value(userId.toString()));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        when(identityService.createUser(any(CreateUserCommand.class)))
                .thenThrow(new ConstraintViolationException(Set.of()));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "email": "invalid-email",
                                  "displayName": "",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid account data"))
                .andExpect(jsonPath("$.detail")
                        .value("One or more account fields are invalid."));
    }

    @Test
    void shouldReturnConflictWhenUsernameAlreadyExists() throws Exception {
        when(identityService.createUser(any(CreateUserCommand.class)))
                .thenThrow(new UsernameAlreadyExistsException("andre"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "andre",
                                  "email": "andre@example.com",
                                  "displayName": "André",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Account data already in use"))
                .andExpect(jsonPath("$.detail")
                        .value("An account cannot be created with the provided credentials."));
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        when(identityService.createUser(any(CreateUserCommand.class)))
                .thenThrow(new EmailAlreadyExistsException(
                        "andre@example.com"
                ));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "andre",
                                  "email": "andre@example.com",
                                  "displayName": "André",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Account data already in use"))
                .andExpect(jsonPath("$.detail")
                        .value("An account cannot be created with the provided credentials."));
    }
}