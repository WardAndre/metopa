package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatePageCommand;
import metopa.publication.PublicationService;
import metopa.publication.ReorderPagesCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldUploadPageForAuthenticatedUser()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        byte[] content =
                "image-content".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "page.jpg",
                        MediaType.IMAGE_JPEG_VALUE,
                        content
                );

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(publicationService.createPage(
                eq(userId),
                any(CreatePageCommand.class)
        )).thenReturn(pageId);

        mockMvc.perform(
                        multipart(
                                "/api/installments/{installmentId}/pages",
                                installmentId
                        )
                                .file(file)
                                .param("number", "1")
                                .principal(() -> "andre")
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id")
                                .value(pageId.toString())
                );

        verify(identityService)
                .findByUsername("andre");

        ArgumentCaptor<CreatePageCommand> commandCaptor =
                ArgumentCaptor.forClass(
                        CreatePageCommand.class
                );

        verify(publicationService)
                .createPage(
                        eq(userId),
                        commandCaptor.capture()
                );

        CreatePageCommand command =
                commandCaptor.getValue();

        assertThat(command.installmentId())
                .isEqualTo(installmentId);

        assertThat(command.number())
                .isEqualTo(1);

        assertThat(command.contentType())
                .isEqualTo("image/jpeg");

        assertThat(command.content())
                .isEqualTo(content);
    }

    @Test
    void shouldDeletePageForAuthenticatedUser()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        mockMvc.perform(
                        delete(
                                "/api/installments/{installmentId}/pages/{pageId}",
                                installmentId,
                                pageId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(
                        status().isNoContent()
                );

        verify(identityService)
                .findByUsername("andre");

        verify(publicationService)
                .deletePage(
                        userId,
                        installmentId,
                        pageId
                );
    }

    @Test
    void shouldReorderPagesForAuthenticatedUser()
            throws Exception {

        UUID userId = UUID.randomUUID();
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

        String requestBody = """
            {
              "pageIds": [
                "%s",
                "%s"
              ]
            }
            """.formatted(
                secondPageId,
                firstPageId
        );

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}/pages/order",
                                installmentId
                        )
                                .principal(() -> "andre")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNoContent());

        verify(identityService)
                .findByUsername("andre");

        ArgumentCaptor<ReorderPagesCommand> commandCaptor =
                ArgumentCaptor.forClass(
                        ReorderPagesCommand.class
                );

        verify(publicationService)
                .reorderPages(
                        eq(userId),
                        eq(installmentId),
                        commandCaptor.capture()
                );

        assertThat(commandCaptor.getValue().pageIds())
                .containsExactly(
                        secondPageId,
                        firstPageId
                );
    }

    @Test
    void shouldRejectEmptyPageOrderRequest()
            throws Exception {

        UUID installmentId = UUID.randomUUID();

        mockMvc.perform(
                        put(
                                "/api/installments/{installmentId}/pages/order",
                                installmentId
                        )
                                .principal(() -> "andre")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "pageIds": []
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }
}