package metopa.publication;

public record PublishedWorkCoverContent(
        String contentType,
        byte[] content
) {

    public PublishedWorkCoverContent {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}