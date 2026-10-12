package metopa.catalog;

import java.util.UUID;

public class WorkCoverNotFoundException
        extends RuntimeException {

    public WorkCoverNotFoundException(UUID workId) {
        super(
                "Cover not found for work: " + workId
        );
    }
}