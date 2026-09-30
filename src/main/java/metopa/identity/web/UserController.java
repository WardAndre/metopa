package metopa.identity.web;

import jakarta.validation.Valid;
import metopa.identity.CreateUserCommand;
import metopa.identity.IdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final IdentityService identityService;

    public UserController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(
            @RequestBody CreateUserRequest request
    ) {
        CreateUserCommand command = new CreateUserCommand(
                request.username(),
                request.email(),
                request.displayName()
        );

        UUID userId = identityService.createUser(command);

        return new CreateUserResponse(userId);
    }
}