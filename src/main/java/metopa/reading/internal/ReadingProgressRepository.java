package metopa.reading.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository
        extends JpaRepository<ReadingProgress, UUID> {

    Optional<ReadingProgress> findByUserIdAndWorkId(
            UUID userId,
            UUID workId
    );
}