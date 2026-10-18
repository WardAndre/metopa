package metopa.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkRepository
        extends JpaRepository<Work, UUID> {

    List<Work> findByOwnerIdOrderByUpdatedAtDesc(
            UUID ownerId
    );
}