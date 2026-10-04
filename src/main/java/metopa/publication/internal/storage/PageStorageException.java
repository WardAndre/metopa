package metopa.publication.internal.storage;

public class PageStorageException extends RuntimeException {

    public PageStorageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}