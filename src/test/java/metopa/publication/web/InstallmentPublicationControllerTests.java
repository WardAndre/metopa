package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void shouldUpdateInstallmentForAuthenticatedUser()
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
                        put(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                                .principal(() -> "andre")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "type": "ISSUE",
                                          "number": 2,
                                          "title": "Issue Two"
                                        }
                                        """)
                )
                .andExpect(
                        status().isNoContent()
                );

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .updateInstallment(
                        eq(userId),
                        eq(installmentId),
                        argThat(command ->
                                command.type()
                                        == InstallmentType.ISSUE
                                        && command.number() == 2
                                        && command.title()
                                        .equals("Issue Two")
                        )
                );
    }
}