package metopa.publication.internal;

import jakarta.persistence.LockModeType;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentRepository
        extends JpaRepository<Installment, UUID> {

    boolean existsByWorkId(UUID workId);

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

    @Query(
            value = """
                    SELECT i.work_id
                    FROM installment i
                    WHERE i.status = 'PUBLISHED'
                    GROUP BY i.work_id
                    ORDER BY MAX(i.updated_at) DESC, i.work_id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT i.work_id)
                    FROM installment i
                    WHERE i.status = 'PUBLISHED'
                    """,
            nativeQuery = true
    )
    Page<UUID> findPublishedWorkIds(
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT i.work_id
                    FROM installment i
                    WHERE i.status = 'PUBLISHED'
                      AND EXISTS (
                          SELECT 1
                          FROM work_genre wg
                          WHERE wg.work_id = i.work_id
                            AND wg.genre = :genre
                      )
                    GROUP BY i.work_id
                    ORDER BY MAX(i.updated_at) DESC, i.work_id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT i.work_id)
                    FROM installment i
                    WHERE i.status = 'PUBLISHED'
                      AND EXISTS (
                          SELECT 1
                          FROM work_genre wg
                          WHERE wg.work_id = i.work_id
                            AND wg.genre = :genre
                      )
                    """,
            nativeQuery = true
    )
    Page<UUID> findPublishedWorkIdsByGenre(
            @Param("genre") String genre,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Installment i WHERE i.id = :id")
    Optional<Installment> findByIdForUpdate(
            @Param("id") UUID id
    );
}