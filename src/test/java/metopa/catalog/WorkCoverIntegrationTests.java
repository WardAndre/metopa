package metopa.catalog;

import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.media.BinaryStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "metopa.storage.root=target/test-work-cover-storage"
        }
)
@AutoConfigureMockMvc
class WorkCoverIntegrationTests {

    private static final String PASSWORD =
            "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private BinaryStorage binaryStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldStoreCoverAndPersistReference()
            throws Exception {

        TestUser user =
                createUser("cover-owner");

        UUID workId =
                createWork(user.id());

        byte[] content =
                "real-cover-content".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        String storageKey =
                (String) row.get(
                        "cover_storage_key"
                );

        String contentType =
                (String) row.get(
                        "cover_content_type"
                );

        assertThat(storageKey)
                .startsWith(
                        "covers/"
                                + workId
                                + "/"
                );

        assertThat(contentType)
                .isEqualTo("image/webp");

        assertThat(
                binaryStorage.read(storageKey)
        ).isEqualTo(content);
    }

    @Test
    void shouldRejectCoverUploadFromAnotherUser()
            throws Exception {

        TestUser owner =
                createUser("cover-owner");

        TestUser anotherUser =
                createUser("cover-other");

        UUID workId =
                createWork(owner.id());

        byte[] content =
                "unauthorized-cover".getBytes();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        content
                );

        MockHttpSession session =
                login(anotherUser.username());

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Work access denied"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT cover_storage_key,
                               cover_content_type
                        FROM work
                        WHERE id = ?
                        """,
                        workId
                );

        assertThat(
                row.get("cover_storage_key")
        ).isNull();

        assertThat(
                row.get("cover_content_type")
        ).isNull();
    }

    @Test
    void shouldRejectUnauthenticatedCoverUpload()
            throws Exception {

        UUID workId = UUID.randomUUID();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        "cover-content".getBytes()
                );

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireCsrfForCoverUpload()
            throws Exception {

        TestUser user =
                createUser("cover-csrf");

        UUID workId =
                createWork(user.id());

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cover.webp",
                        "image/webp",
                        "cover-content".getBytes()
                );

        MockHttpSession session =
                login(user.username());

        mockMvc.perform(
                        multipart(
                                "/api/works/{workId}/cover",
                                workId
                        )
                                .file(file)
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .session(session)
                )
                .andExpect(status().isForbidden());
    }

    private UUID createWork(UUID ownerId) {
        return catalogService.createWork(
                new CreateWorkCommand(
                        ownerId,
                        "Covered Work "
                                + UUID.randomUUID(),
                        "A work with a cover.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                )
        );
    }

    private TestUser createUser(
            String prefix
    ) {
        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);

        String username =
                prefix + "-" + suffix;

        identityService.createUser(
                new CreateUserCommand(
                        username,
                        username + "@example.com",
                        "Cover Test User",
                        PASSWORD
                )
        );

        UserReference user =
                identityService.findByUsername(
                        username
                );

        return new TestUser(
                user.id(),
                user.username()
        );
    }

    private MockHttpSession login(
            String username
    ) throws Exception {

        MvcResult result =
                mockMvc.perform(
                                formLogin(
                                        "/api/auth/login"
                                )
                                        .user(username)
                                        .password(PASSWORD)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                authenticated()
                                        .withUsername(username)
                        )
                        .andReturn();

        return (MockHttpSession) result
                .getRequest()
                .getSession(false);
    }

    private record TestUser(
            UUID id,
            String username
    ) {
    }
}