package metopa.library;

public class WorkNotAvailableForLibraryException
        extends RuntimeException {

    public WorkNotAvailableForLibraryException() {
        super(
                "The requested work is not available for the library."
        );
    }
}