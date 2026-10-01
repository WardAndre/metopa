package metopa.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserAccountRepositoryTests {

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
}