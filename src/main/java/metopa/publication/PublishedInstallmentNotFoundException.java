package metopa.publication;

import java.util.UUID;

public class PublishedInstallmentNotFoundException
        extends RuntimeException {

    public PublishedInstallmentNotFoundException(
            UUID installmentId
    ) {
        super(
                "Published installment not found: "
                        + installmentId
        );
    }
}