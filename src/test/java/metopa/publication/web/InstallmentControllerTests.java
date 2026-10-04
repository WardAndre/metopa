package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstallmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class InstallmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldCreateInstallmentForAuthenticatedUser()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(userId, "andre")
                );

        when(publicationService.createInstallment(
                eq(userId),
                any()
        )).thenReturn(installmentId);

        mockMvc.perform(
                        post(
                                "/api/works/{workId}/installments",
                                workId
                        )
                                .principal(() -> "andre")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "type": "CHAPTER",
                                          "number": 1,
                                          "title": "The Beginning"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        installmentId.toString()
                                )
                );

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .createInstallment(
                        eq(userId),
                        any()
                );
    }
}