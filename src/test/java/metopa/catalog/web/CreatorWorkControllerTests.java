package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.catalog.Genre;
import metopa.catalog.WorkSummary;
import metopa.catalog.WorkType;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkDetails;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorWorkController.class)
@AutoConfigureMockMvc(addFilters = false)
class CreatorWorkControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldListWorksForAuthenticatedUser()
            throws Exception {

        UUID ownerId = UUID.randomUUID();
        UUID firstWorkId = UUID.randomUUID();
        UUID secondWorkId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                ownerId,
                                "andre"
                        )
                );

        when(
                catalogService.findWorksByOwner(
                        ownerId
                )
        ).thenReturn(
                List.of(
                        new WorkSummary(
                                firstWorkId,
                                "Metopa Origins",
                                WorkType.GRAPHIC_NOVEL,
                                Set.of(
                                        Genre.SCI_FI,
                                        Genre.ADVENTURE
                                ),
                                true
                        ),
                        new WorkSummary(
                                secondWorkId,
                                "Second Work",
                                WorkType.COMIC,
                                Set.of(
                                        Genre.ACTION
                                ),
                                false
                        )
                )
        );

        mockMvc.perform(
                        get("/api/me/works")
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
                                        firstWorkId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].title")
                                .value(
                                        "Metopa Origins"
                                )
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value(
                                        "GRAPHIC_NOVEL"
                                )
                )
                .andExpect(
                        jsonPath("$[0].genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$[0].hasCover")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(
                                        secondWorkId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[1].title")
                                .value(
                                        "Second Work"
                                )
                )
                .andExpect(
                        jsonPath("$[1].type")
                                .value("COMIC")
                )
                .andExpect(
                        jsonPath("$[1].genres[0]")
                                .value("ACTION")
                )
                .andExpect(
                        jsonPath("$[1].hasCover")
                                .value(false)
                );

        verify(identityService)
                .findByUsername("andre");

        verify(catalogService)
                .findWorksByOwner(
                        ownerId
                );
    }

    @Test
    void shouldReturnWorkDetailsForAuthenticatedOwner()
            throws Exception {

        UUID ownerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                ownerId,
                                "andre"
                        )
                );

        when(
                catalogService.findCreatorWorkDetails(
                        ownerId,
                        workId
                )
        ).thenReturn(
                new WorkDetails(
                        workId,
                        "Metopa Origins",
                        "A science fiction graphic novel.",
                        WorkType.GRAPHIC_NOVEL,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE,
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        ),
                        true
                )
        );

        mockMvc.perform(
                        get(
                                "/api/me/works/{workId}",
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
                        jsonPath("$.id")
                                .value(
                                        workId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Metopa Origins"
                                )
                )
                .andExpect(
                        jsonPath("$.description")
                                .value(
                                        "A science fiction graphic novel."
                                )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "GRAPHIC_NOVEL"
                                )
                )
                .andExpect(
                        jsonPath("$.readingDirection")
                                .value(
                                        "LEFT_TO_RIGHT"
                                )
                )
                .andExpect(
                        jsonPath("$.presentationMode")
                                .value(
                                        "SINGLE_PAGE"
                                )
                )
                .andExpect(
                        jsonPath("$.genres")
                                .value(
                                        containsInAnyOrder(
                                                "SCI_FI",
                                                "ADVENTURE"
                                        )
                                )
                )
                .andExpect(
                        jsonPath("$.hasCover")
                                .value(true)
                );

        verify(identityService)
                .findByUsername("andre");

        verify(catalogService)
                .findCreatorWorkDetails(
                        ownerId,
                        workId
                );
    }
}