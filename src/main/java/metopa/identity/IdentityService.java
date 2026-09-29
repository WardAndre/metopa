package metopa.identity;

import metopa.identity.internal.UserAccount;
import metopa.identity.internal.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;
import java.util.Locale;

@Service
@Validated
public class IdentityService {

    private final UserAccountRepository repository;

    public IdentityService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID createUser(@Valid CreateUserCommand command) {
        String username = command.username()
                .trim()
                .toLowerCase(Locale.ROOT);

        String email = command.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String displayName = command.displayName().trim();

        if (repository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }

        if (repository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        UserAccount account = new UserAccount(
                username,
                email,
                displayName
        );

        UserAccount saved = repository.save(account);

        return saved.getId();
    }
}