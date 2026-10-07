package metopa.library;

public class LibraryEntryAlreadyExistsException
        extends RuntimeException {

    public LibraryEntryAlreadyExistsException() {
        super(
                "The work is already present in the user's library."
        );
    }
}