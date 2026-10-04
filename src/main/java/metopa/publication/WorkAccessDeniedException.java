package metopa.publication;

public class WorkAccessDeniedException extends RuntimeException {

    public WorkAccessDeniedException() {
        super("The authenticated user does not own this work.");
    }
}