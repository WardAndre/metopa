package metopa.media;

public class BinaryStorageException
        extends RuntimeException {

    public BinaryStorageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}