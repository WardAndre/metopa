package metopa.publication;

import java.util.UUID;

public record PublishedInstallmentSummary(
        UUID id,
        InstallmentType type,
        int number,
        String title
) {
}