package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatorInstallmentSummary;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationService;
import metopa.publication.PublicationStatus;
import metopa.publication.CreatorInstallmentView;
import metopa.publication.PageView;
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

    @Test
    void shouldReturnCreatorInstallmentDetail()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID firstPageId = UUID.randomUUID();
        UUID secondPageId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                publicationService
                        .findCreatorInstallment(
                                userId,
                                installmentId
                        )
        ).thenReturn(
                new CreatorInstallmentView(
                        installmentId,
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Chapter One",
                        PublicationStatus.DRAFT,
                        List.of(
                                new PageView(
                                        firstPageId,
                                        1,
                                        "image/jpeg"
                                ),
                                new PageView(
                                        secondPageId,
                                        2,
                                        "image/webp"
                                )
                        )
                )
        );

        mockMvc.perform(
                        get(
                                "/api/me/installments/{installmentId}",
                                installmentId
                        )
                                .principal(
                                        () -> "andre"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        installmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.workId")
                                .value(
                                        workId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("CHAPTER")
                )
                .andExpect(
                        jsonPath("$.number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Chapter One")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("DRAFT")
                )
                .andExpect(
                        jsonPath("$.pages.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.pages[0].id")
                                .value(
                                        firstPageId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.pages[0].number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.pages[0].contentType")
                                .value("image/jpeg")
                )
                .andExpect(
                        jsonPath("$.pages[1].id")
                                .value(
                                        secondPageId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.pages[1].number")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.pages[1].contentType")
                                .value("image/webp")
                );

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .findCreatorInstallment(
                        userId,
                        installmentId
                );
    }
}