package metopa.identity.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityUserDetailsServiceTests {

    @Mock
    private UserAccountRepository repository;

    @InjectMocks
    private IdentityUserDetailsService userDetailsService;

    @Test
    void shouldLoadUserByNormalizedUsername() {
        UserAccount account = new UserAccount(
                "andre",
                "andre@example.com",
                "André",
                "{bcrypt}encoded-password"
        );

        when(repository.findByUsername("andre"))
                .thenReturn(Optional.of(account));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("  ANDRE  ");

        assertThat(userDetails.getUsername())
                .isEqualTo("andre");

        assertThat(userDetails.getPassword())
                .isEqualTo("{bcrypt}encoded-password");

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldRejectUnknownUsername() {
        when(repository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("unknown")
        )
                .isInstanceOf(UsernameNotFoundException.class);
    }
}