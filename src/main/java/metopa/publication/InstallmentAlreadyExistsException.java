package metopa.publication;

public class InstallmentAlreadyExistsException
        extends RuntimeException {

    public InstallmentAlreadyExistsException() {
        super(
                "An installment with this type and number " +
                        "already exists for the work."
        );
    }
}