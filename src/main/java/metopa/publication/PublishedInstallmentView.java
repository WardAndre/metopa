package metopa.publication;

import java.util.List;
import java.util.UUID;

public record PublishedInstallmentView(
        UUID id,
        UUID workId,
        InstallmentType type,
        int number,
        String title,
        List<PageView> pages
) {

    public PublishedInstallmentView {
        pages = List.copyOf(pages);
    }
}