
package metopa.identity.internal;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserAccountRepositoryTests {

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistUserAccount() {
        UserAccount account = new UserAccount(
                "andre",
                "andre@example.com",
                "André",
                "{bcrypt}encoded-password"
        );

        UserAccount saved = repository.saveAndFlush(account);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("andre");
        assertThat(saved.getEmail()).isEqualTo("andre@example.com");
        assertThat(saved.getDisplayName()).isEqualTo("André");
        assertThat(saved.getCreatedAt()).isNotNull();

        String passwordHash = jdbcTemplate.queryForObject(
                """
                SELECT password_hash
                FROM user_account
                WHERE id = ?
                """,
                String.class,
                saved.getId()
        );

        assertThat(passwordHash)
                .isEqualTo("{bcrypt}encoded-password");
    }

    @Test
    void shouldPersistDefaultUserRole() {
        UserAccount account = new UserAccount(
                "reader",
                "reader@example.com",
                "Test Reader",
                "{bcrypt}encoded-password"
        );

        repository.saveAndFlush(account);

        entityManager.clear();

        UserAccount persisted = repository
                .findById(account.getId())
                .orElseThrow();

        assertThat(persisted.getRole())
                .isEqualTo(AccountRole.USER);

        String persistedRole = jdbcTemplate.queryForObject(
                """
                SELECT role
                FROM user_account
                WHERE id = ?
                """,
                String.class,
                account.getId()
        );

        assertThat(persistedRole)
                .isEqualTo("USER");
    }

    @Test
    void shouldLoadAdminRoleFromDatabase() {
        UserAccount account = new UserAccount(
                "admin",
                "admin@example.com",
                "Test Administrator",
                "{bcrypt}encoded-password"
        );

        repository.saveAndFlush(account);

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE user_account
                SET role = ?
                WHERE id = ?
                """,
                AccountRole.ADMIN.name(),
                account.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        entityManager.clear();

        UserAccount persisted = repository
                .findById(account.getId())
                .orElseThrow();

        assertThat(persisted.getRole())
                .isEqualTo(AccountRole.ADMIN);
    }

    @Test
    void shouldRejectInvalidAccountRoleAtDatabaseLevel() {
        UserAccount account = new UserAccount(
                "invalid-role",
                "invalid-role@example.com",
                "Invalid Role Test",
                "{bcrypt}encoded-password"
        );

        repository.saveAndFlush(account);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                UPDATE user_account
                SET role = ?
                WHERE id = ?
                """,
                "MODERATOR",
                account.getId()
        )).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }
}
