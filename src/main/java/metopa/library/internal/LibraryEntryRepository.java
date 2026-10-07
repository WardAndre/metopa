package metopa.library.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LibraryEntryRepository
        extends JpaRepository<LibraryEntry, UUID> {

    boolean existsByUserIdAndWorkId(
            UUID userId,
            UUID workId
    );

    List<LibraryEntry> findByUserIdOrderByAddedAtDesc(
            UUID userId
    );
}