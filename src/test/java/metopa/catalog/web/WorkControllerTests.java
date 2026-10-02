package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldCreateWorkForAuthenticatedUser() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(new UserReference(ownerId, "andre"));

        when(catalogService.createWork(any()))
                .thenReturn(workId);

        mockMvc.perform(
                        post("/api/works")
                                .principal(() -> "andre")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "Metopa Origins",
                                          "description": "A graphic novel.",
                                          "type": "GRAPHIC_NOVEL",
                                          "readingDirection": "LEFT_TO_RIGHT",
                                          "presentationMode": "SINGLE_PAGE"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id")
                        .value(workId.toString()));

        verify(identityService)
                .findByUsername("andre");

        verify(catalogService)
                .createWork(any());
    }
}