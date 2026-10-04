package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstallmentPublicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class InstallmentPublicationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldPublishInstallmentForAuthenticatedUser()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        mockMvc.perform(
                        post(
                                "/api/installments/{installmentId}/publish",
                                installmentId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(status().isNoContent());

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .publishInstallment(
                        userId,
                        installmentId
                );
    }
}