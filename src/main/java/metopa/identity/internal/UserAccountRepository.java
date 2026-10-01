package metopa.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<UserAccount> findByUsername(String username);
}