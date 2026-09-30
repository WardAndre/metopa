package metopa.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserAccountRepositoryTests {

    @Autowired
    private UserAccountRepository repository;

    @Test
    void shouldPersistUserAccount() {
        UserAccount account = new UserAccount(
                "andre",
                "andre@example.com",
                "André"
        );

        UserAccount saved = repository.save(account);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("andre");
        assertThat(saved.getEmail()).isEqualTo("andre@example.com");
        assertThat(saved.getDisplayName()).isEqualTo("André");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}