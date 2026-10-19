package metopa.publication;

import java.util.UUID;

public record CreatorInstallmentSummary(
        UUID id,
        InstallmentType type,
        int number,
        String title,
        PublicationStatus status
) {
}