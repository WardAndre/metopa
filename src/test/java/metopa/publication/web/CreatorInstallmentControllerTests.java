package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatorInstallmentSummary;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationService;
import metopa.publication.PublicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorInstallmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CreatorInstallmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldListInstallmentsForAuthenticatedWorkOwner()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID firstInstallmentId = UUID.randomUUID();
        UUID secondInstallmentId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                publicationService
                        .findCreatorInstallments(
                                userId,
                                workId
                        )
        ).thenReturn(
                List.of(
                        new CreatorInstallmentSummary(
                                firstInstallmentId,
                                InstallmentType.CHAPTER,
                                1,
                                "Chapter One",
                                PublicationStatus.PUBLISHED
                        ),
                        new CreatorInstallmentSummary(
                                secondInstallmentId,
                                InstallmentType.CHAPTER,
                                2,
                                "Chapter Two",
                                PublicationStatus.DRAFT
                        )
                )
        );

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}/installments",
                                workId
                        )
                                .principal(
                                        () -> "andre"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(
                                        firstInstallmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value("CHAPTER")
                )
                .andExpect(
                        jsonPath("$[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].title")
                                .value("Chapter One")
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("PUBLISHED")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(
                                        secondInstallmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[1].type")
                                .value("CHAPTER")
                )
                .andExpect(
                        jsonPath("$[1].number")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[1].title")
                                .value("Chapter Two")
                )
                .andExpect(
                        jsonPath("$[1].status")
                                .value("DRAFT")
                );

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .findCreatorInstallments(
                        userId,
                        workId
                );
    }
}