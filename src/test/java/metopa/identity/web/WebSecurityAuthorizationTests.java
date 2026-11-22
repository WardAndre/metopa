
package metopa.identity.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WebSecurityAuthorizationTests.AdminProbeController.class)
class WebSecurityAuthorizationTests {

    private static final String ADMIN_PATH =
            "/api/admin/_authorization-probe";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectUnauthenticatedAdminRequest() throws Exception {
        mockMvc.perform(get(ADMIN_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRegularUserFromAdminEndpoint() throws Exception {
        mockMvc.perform(get(ADMIN_PATH)
                        .with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get(ADMIN_PATH)
                        .with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRequireCsrfForAdminWriteOperation() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectRegularUserFromAdminWriteOperation()
            throws Exception {

        mockMvc.perform(post(ADMIN_PATH)
                        .with(user("reader").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminWriteOperationWithCsrf()
            throws Exception {

        mockMvc.perform(post(ADMIN_PATH)
                        .with(user("admin").roles("USER", "ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @RestController
    static class AdminProbeController {

        @GetMapping(ADMIN_PATH)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void read() {
        }

        @PostMapping(ADMIN_PATH)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void write() {
        }
    }
}
