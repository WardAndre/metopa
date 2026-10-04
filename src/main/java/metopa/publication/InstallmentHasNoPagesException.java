package metopa.publication;

public class InstallmentHasNoPagesException
        extends RuntimeException {

    public InstallmentHasNoPagesException() {
        super(
                "An installment must contain at least one page before publication."
        );
    }
}