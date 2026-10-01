package metopa.identity.internal;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class IdentityUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    IdentityUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalizedUsername = username
                .trim()
                .toLowerCase(Locale.ROOT);

        UserAccount account = repository
                .findByUsername(normalizedUsername)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid username or password"
                        )
                );

        return User.withUsername(account.getUsername())
                .password(account.passwordHash())
                .authorities("ROLE_USER")
                .build();
    }
}