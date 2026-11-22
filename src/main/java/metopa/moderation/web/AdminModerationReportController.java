package metopa.moderation.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import metopa.identity.IdentityService;
import metopa.moderation.ModerationReportSummary;
import metopa.moderation.ModerationService;
import metopa.moderation.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/moderation/reports")
public class AdminModerationReportController {

    private final ModerationService moderationService;
    private final IdentityService identityService;

    public AdminModerationReportController(
            ModerationService moderationService,
            IdentityService identityService
    ) {
        this.moderationService = moderationService;
        this.identityService = identityService;
    }

    @GetMapping
    public ModerationReportPageResponse findReports(
            @RequestParam(required = false)
            ReportStatus status,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(50)
            int size
    ) {
        Page<ModerationReportSummary> reports =
                moderationService.findReports(status, page, size);

        return ModerationReportPageResponse.from(reports);
    }

    @PostMapping("/{reportId}/start-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startReview(
            @PathVariable UUID reportId,
            @Valid @RequestBody StartReviewRequest request,
            Principal principal
    ) {
        UUID adminUserId = identityService
                .findByUsername(principal.getName())
                .id();

        moderationService.startReview(
                reportId,
                adminUserId,
                request.note()
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleReportNotFound(
            NoSuchElementException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Moderation report not found."
        );

        problem.setTitle("Moderation report not found");

        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleInvalidTransition(
            IllegalStateException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The requested moderation action is not allowed in the current state."
        );

        problem.setTitle("Invalid moderation transition");

        return problem;
    }

    @PostMapping("/{reportId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReportRequest request,
            Principal principal
    ) {
        UUID adminUserId = identityService
                .findByUsername(principal.getName())
                .id();

        switch (request.decision()) {
            case NO_VIOLATION ->
                    moderationService.resolveNoViolation(
                            reportId,
                            adminUserId,
                            request.note()
                    );

            case WORK_RESTRICTED ->
                    moderationService.resolveWorkRestriction(
                            reportId,
                            adminUserId,
                            request.note()
                    );
        }
    }

    @PostMapping("/{reportId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreWork(
            @PathVariable UUID reportId,
            @Valid @RequestBody RestoreWorkRequest request,
            Principal principal
    ) {
        UUID adminUserId = identityService
                .findByUsername(principal.getName())
                .id();

        moderationService.restoreWork(
                reportId,
                adminUserId,
                request.note()
        );
    }
}
