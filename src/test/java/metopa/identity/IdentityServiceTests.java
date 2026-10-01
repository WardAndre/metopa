package metopa.identity;

import metopa.identity.internal.UserAccount;
import metopa.identity.internal.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyString;

import java.util.UUID;


@ExtendWith(MockitoExtension.class)
class IdentityServiceTests {

    @Mock
    private UserAccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private IdentityService identityService;

    @Test
    void shouldRejectDuplicateUsername() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "andre@example.com",
                "André",
                "StrongPassword123!"
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(true);

        assertThatThrownBy(() -> identityService.createUser(command))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(repository, never()).save(
                org.mockito.ArgumentMatchers.any(UserAccount.class)
        );
        verify(passwordEncoder, never()).encode(anyString());
        verify(repository, never()).save(any(UserAccount.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "andre@example.com",
                "André",
                "StrongPassword123!"
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(false);

        when(repository.existsByEmail("andre@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> identityService.createUser(command))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(repository, never()).save(
                org.mockito.ArgumentMatchers.any(UserAccount.class)
        );
        verify(passwordEncoder, never()).encode(anyString());
        verify(repository, never()).save(any(UserAccount.class));
    }

    @Test
    void shouldCreateUser() {
        CreateUserCommand command = new CreateUserCommand(
                "  Andre  ",
                "  Andre@Example.com  ",
                "  André Ward  ",
                "StrongPassword123!"
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(false);

        when(repository.existsByEmail("andre@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("StrongPassword123!"))
                .thenReturn("{bcrypt}encoded-password");

        when(repository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID userId = identityService.createUser(command);

        assertThat(userId).isNotNull();

        verify(passwordEncoder)
                .encode("StrongPassword123!");

        verify(repository)
                .save(any(UserAccount.class));
    }

    @Test
    void shouldRejectDuplicateUsernameAfterNormalization() {
        CreateUserCommand command = new CreateUserCommand(
                "  ANDRE  ",
                "another@example.com",
                "André",
                "StrongPassword123!"
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(true);

        assertThatThrownBy(() -> identityService.createUser(command))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(repository, never()).save(any(UserAccount.class));
        verify(passwordEncoder, never()).encode(anyString());
        verify(repository, never()).save(any(UserAccount.class));
    }
}