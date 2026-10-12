package metopa.catalog;

public record WorkCoverContent(
        String contentType,
        byte[] content
) {

    public WorkCoverContent {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}