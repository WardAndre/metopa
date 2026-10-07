package metopa.library.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.library.LibraryEntryView;
import metopa.library.LibraryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/library")
class LibraryController {

    private final LibraryService libraryService;
    private final IdentityService identityService;

    LibraryController(
            LibraryService libraryService,
            IdentityService identityService
    ) {
        this.libraryService = libraryService;
        this.identityService = identityService;
    }

    @PostMapping("/works/{workId}")
    @ResponseStatus(HttpStatus.CREATED)
    AddLibraryEntryResponse addWork(
            @PathVariable UUID workId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        UUID entryId =
                libraryService.addWork(
                        user.id(),
                        workId
                );

        return new AddLibraryEntryResponse(
                entryId
        );
    }

    @GetMapping
    List<LibraryEntryResponse> findLibrary(
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        List<LibraryEntryView> library =
                libraryService.findLibrary(
                        user.id()
                );

        return library
                .stream()
                .map(LibraryEntryResponse::from)
                .toList();
    }
}