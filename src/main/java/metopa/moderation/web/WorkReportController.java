package metopa.moderation.web;

import jakarta.validation.Valid;
import metopa.identity.IdentityService;
import metopa.moderation.CreateReportCommand;
import metopa.moderation.ModerationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/works/{workId}/reports")
public class WorkReportController {

    private final ModerationService moderationService;
    private final IdentityService identityService;

    public WorkReportController(
            ModerationService moderationService,
            IdentityService identityService
    ) {
        this.moderationService = moderationService;
        this.identityService = identityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateReportResponse createReport(
            @PathVariable UUID workId,
            @Valid @RequestBody CreateReportRequest request,
            Principal principal
    ) {
        UUID reporterUserId = identityService
                .findByUsername(principal.getName())
                .id();

        UUID reportId = moderationService.createReport(
                new CreateReportCommand(
                        reporterUserId,
                        workId,
                        request.reason(),
                        request.description()
                )
        );

        return new CreateReportResponse(reportId);
    }
}