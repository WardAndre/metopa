package metopa.publication.internal;

import metopa.publication.InstallmentType;
import metopa.publication.PublicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
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

    List<Installment> findByWorkIdOrderByNumberAscCreatedAtAsc(
            UUID workId
    );

    List<Installment> findByWorkIdAndStatusOrderByNumberAscCreatedAtAsc(
            UUID workId,
            PublicationStatus status
    );

    List<Installment> findByStatusOrderByUpdatedAtDesc(
            PublicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Installment i WHERE i.id = :id")
    Optional<Installment> findByIdForUpdate(
            @Param("id") UUID id
    );
}