package metopa.publication.internal;

import metopa.publication.InstallmentType;
import metopa.publication.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InstallmentRepository
        extends JpaRepository<Installment, UUID> {

    boolean existsByWorkIdAndTypeAndNumber(
            UUID workId,
            InstallmentType type,
            int number
    );

    boolean existsByWorkIdAndStatus(
            UUID workId,
            PublicationStatus status
    );
}