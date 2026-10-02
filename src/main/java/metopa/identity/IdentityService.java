package metopa.identity;

import metopa.identity.internal.UserAccount;
import metopa.identity.internal.UserAccountRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.Valid;

import java.util.UUID;
import java.util.Locale;

@Service
@Validated
public class IdentityService {

    private final UserAccountRepository repository;

    private final PasswordEncoder passwordEncoder;

    public IdentityService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
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

        String passwordHash =
                passwordEncoder.encode(command.password());

        UserAccount account = new UserAccount(
                command.username(),
                command.email(),
                command.displayName(),
                passwordHash
        );

        UserAccount saved = repository.save(account);

        return saved.getId();
    }

    @Transactional(readOnly = true)
    public UserReference findByUsername(String username) {
        String normalizedUsername = username
                .trim()
                .toLowerCase(Locale.ROOT);

        UserAccount account = repository
                .findByUsername(normalizedUsername)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        return new UserReference(
                account.getId(),
                account.getUsername()
        );
    }
}