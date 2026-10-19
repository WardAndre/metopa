package metopa.publication;

import java.util.List;
import java.util.UUID;

public record CreatorInstallmentView(
        UUID id,
        UUID workId,
        InstallmentType type,
        int number,
        String title,
        PublicationStatus status,
        List<PageView> pages
) {

    public CreatorInstallmentView {
        pages = List.copyOf(pages);
    }
}