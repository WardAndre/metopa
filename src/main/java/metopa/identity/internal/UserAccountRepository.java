package metopa.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {
}