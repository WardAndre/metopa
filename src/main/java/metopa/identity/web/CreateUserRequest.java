package metopa.identity.web;

public record CreateUserRequest(
        String username,
        String email,
        String displayName,
        String password
) {
}