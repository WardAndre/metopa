package metopa.publication.internal;

import metopa.publication.InstallmentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class PageRepositoryTests {

    @Autowired
    private InstallmentRepository installmentRepository;

    @Autowired
    private PageRepository pageRepository;

    @Test
    void shouldPersistPageForInstallment() {
        Installment installment = new Installment(
                java.util.UUID.randomUUID(),
                InstallmentType.CHAPTER,
                1,
                "The Beginning"
        );

        Installment savedInstallment =
                installmentRepository.saveAndFlush(installment);

        Page page = new Page(
                savedInstallment.getId(),
                1,
                "works/example/chapter-1/page-1.jpg",
                "image/jpeg"
        );

        Page saved =
                pageRepository.saveAndFlush(page);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getInstallmentId())
                .isEqualTo(savedInstallment.getId());
        assertThat(saved.getNumber()).isEqualTo(1);
        assertThat(saved.getStorageKey())
                .isEqualTo(
                        "works/example/chapter-1/page-1.jpg"
                );
        assertThat(saved.getContentType())
                .isEqualTo("image/jpeg");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}