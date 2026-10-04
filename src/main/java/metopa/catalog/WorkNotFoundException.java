package metopa.catalog;

import java.util.UUID;

public class WorkNotFoundException extends RuntimeException {

    public WorkNotFoundException(UUID workId) {
        super("Work not found: " + workId);
    }
}