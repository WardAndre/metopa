package metopa.reading.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.reading.ReadingProgressView;
import metopa.reading.ReadingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/reading/progress")
class ReadingController {

    private final ReadingService readingService;
    private final IdentityService identityService;

    ReadingController(
            ReadingService readingService,
            IdentityService identityService
    ) {
        this.readingService = readingService;
        this.identityService = identityService;
    }

    @PutMapping("/pages/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveProgress(
            @PathVariable UUID pageId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        readingService.saveProgress(
                user.id(),
                pageId
        );
    }

    @GetMapping("/works/{workId}")
    ResponseEntity<ReadingProgressResponse> findProgress(
            @PathVariable UUID workId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        Optional<ReadingProgressView> progress =
                readingService.findProgress(
                        user.id(),
                        workId
                );

        return ResponseEntity.of(
                progress.map(
                        ReadingProgressResponse::from
                )
        );
    }
}