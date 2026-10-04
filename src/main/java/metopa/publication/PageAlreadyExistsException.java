package metopa.publication;

public class PageAlreadyExistsException
        extends RuntimeException {

    public PageAlreadyExistsException() {
        super(
                "A page with this number already exists " +
                        "for the installment."
        );
    }
}