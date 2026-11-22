package metopa.identity.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTests {

    @Test
    void shouldAssignUserRoleToNewAccount() {
        UserAccount account = new UserAccount(
                "reader",
                "reader@example.com",
                "Test Reader",
                "encoded-password"
        );

        assertThat(account.getRole())
                .isEqualTo(AccountRole.USER);
    }
}