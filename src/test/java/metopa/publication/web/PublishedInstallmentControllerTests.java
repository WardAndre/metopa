package metopa.publication.web;

import metopa.publication.InstallmentType;
import metopa.publication.PageView;
import metopa.publication.PublicationService;
import metopa.publication.PublishedInstallmentView;
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

@WebMvcTest(PublishedInstallmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublishedInstallmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void shouldReturnPublishedInstallment()
            throws Exception {

        UUID installmentId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID firstPageId = UUID.randomUUID();
        UUID secondPageId = UUID.randomUUID();

        PublishedInstallmentView view =
                new PublishedInstallmentView(
                        installmentId,
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "The Beginning",
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
                );

        when(
                publicationService
                        .findPublishedInstallment(
                                installmentId
                        )
        ).thenReturn(view);

        mockMvc.perform(
                        get(
                                "/api/installments/{installmentId}",
                                installmentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        installmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.workId")
                                .value(workId.toString())
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
                                .value("The Beginning")
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
                        jsonPath("$.pages[1].number")
                                .value(2)
                );

        verify(publicationService)
                .findPublishedInstallment(
                        installmentId
                );
    }
}