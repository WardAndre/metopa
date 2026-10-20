package metopa.publication;

import java.util.UUID;

public class PageNotFoundException
        extends RuntimeException {

    public PageNotFoundException(UUID pageId) {
        super("Page not found: " + pageId);
    }
}