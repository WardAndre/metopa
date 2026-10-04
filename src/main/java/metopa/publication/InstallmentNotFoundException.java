package metopa.publication;

import java.util.UUID;

public class InstallmentNotFoundException
        extends RuntimeException {

    public InstallmentNotFoundException(UUID installmentId) {
        super("Installment not found: " + installmentId);
    }
}