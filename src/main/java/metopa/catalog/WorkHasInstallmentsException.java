package metopa.catalog;

public class WorkHasInstallmentsException
        extends RuntimeException {

    public WorkHasInstallmentsException() {
        super(
                "A work with installments cannot be deleted."
        );
    }
}
