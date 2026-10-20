package metopa.publication;

public record CreatorPageContent(
        String contentType,
        byte[] content
) {

    public CreatorPageContent {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}