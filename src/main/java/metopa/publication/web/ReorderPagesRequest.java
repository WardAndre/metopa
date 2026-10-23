package metopa.publication.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

record ReorderPagesRequest(
        @NotEmpty
        List<@NotNull UUID> pageIds
) {
}