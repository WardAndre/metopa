package metopa.publication;

public class InvalidPageOrderException
        extends RuntimeException {

    public InvalidPageOrderException() {
        super(
                "Page order must contain every page exactly once."
        );
    }
}