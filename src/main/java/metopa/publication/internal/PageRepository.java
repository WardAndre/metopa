package metopa.publication.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PageRepository
        extends JpaRepository<Page, UUID> {

    boolean existsByInstallmentIdAndNumber(
            UUID installmentId,
            int number
    );

    boolean existsByInstallmentId(
            UUID installmentId
    );
}