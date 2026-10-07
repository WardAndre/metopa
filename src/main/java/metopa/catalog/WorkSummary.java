package metopa.catalog;

import java.util.UUID;

public record WorkSummary(
        UUID id,
        String title,
        WorkType type
) {
}