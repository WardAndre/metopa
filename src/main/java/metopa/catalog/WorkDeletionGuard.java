package metopa.catalog;

import java.util.UUID;

public interface WorkDeletionGuard {

    boolean hasInstallments(UUID workId);
}
