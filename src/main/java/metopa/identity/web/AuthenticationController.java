package metopa.identity.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthenticationController {

    @GetMapping("/me")
    CurrentUserResponse currentUser(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName());
    }
}