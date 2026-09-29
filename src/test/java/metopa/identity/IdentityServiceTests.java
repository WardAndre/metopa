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

import java.util.UUID;


@ExtendWith(MockitoExtension.class)
class IdentityServiceTests {

    @Mock
    private UserAccountRepository repository;

    @InjectMocks
    private IdentityService identityService;

    @Test
    void shouldRejectDuplicateUsername() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "andre@example.com",
                "André"
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(true);

        assertThatThrownBy(() -> identityService.createUser(command))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(repository, never()).save(
                org.mockito.ArgumentMatchers.any(UserAccount.class)
        );
    }

    @Test
    void shouldRejectDuplicateEmail() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "andre@example.com",
                "André"
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
    }

    @Test
    void shouldCreateUser() {
        CreateUserCommand command = new CreateUserCommand(
                "  Andre  ",
                "  Andre@Example.com  ",
                "  André Ward  "
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(false);

        when(repository.existsByEmail("andre@example.com"))
                .thenReturn(false);

        when(repository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID userId = identityService.createUser(command);

        assertThat(userId).isNotNull();

        ArgumentCaptor<UserAccount> accountCaptor =
                ArgumentCaptor.forClass(UserAccount.class);

        verify(repository).save(accountCaptor.capture());

        UserAccount savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getUsername()).isEqualTo("andre");
        assertThat(savedAccount.getEmail()).isEqualTo("andre@example.com");
        assertThat(savedAccount.getDisplayName()).isEqualTo("André Ward");
    }

    @Test
    void shouldRejectDuplicateUsernameAfterNormalization() {
        CreateUserCommand command = new CreateUserCommand(
                "  ANDRE  ",
                "another@example.com",
                "André"
        );

        when(repository.existsByUsername("andre"))
                .thenReturn(true);

        assertThatThrownBy(() -> identityService.createUser(command))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(repository, never()).save(any(UserAccount.class));
    }
}