package metopa.library.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.library.LibraryService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibraryController.class)
@AutoConfigureMockMvc(addFilters = false)
class LibraryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryService libraryService;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void shouldAddWorkToAuthenticatedUsersLibrary()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        when(identityService.findByUsername("andre"))
                .thenReturn(
                        new UserReference(
                                userId,
                                "andre"
                        )
                );

        when(
                libraryService.addWork(
                        userId,
                        workId
                )
        ).thenReturn(entryId);

        mockMvc.perform(
                        post(
                                "/api/library/works/{workId}",
                                workId
                        )
                                .principal(() -> "andre")
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        entryId.toString()
                                )
                );

        verify(identityService)
                .findByUsername("andre");

        verify(libraryService)
                .addWork(
                        userId,
                        workId
                );
    }
}