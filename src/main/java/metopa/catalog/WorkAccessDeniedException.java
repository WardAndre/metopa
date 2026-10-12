package metopa.catalog;

public class WorkAccessDeniedException
        extends RuntimeException {

    public WorkAccessDeniedException() {
        super("User does not own the requested work.");
    }
}