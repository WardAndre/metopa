package metopa.publication;

import java.util.UUID;

public record PageView(
        UUID id,
        int number,
        String contentType
) {
}