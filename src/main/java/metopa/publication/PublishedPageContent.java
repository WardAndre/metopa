package metopa.publication;

public record PublishedPageContent(
        String contentType,
        byte[] content
) {

    public PublishedPageContent {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}