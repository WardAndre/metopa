package metopa.publication;

public class PublishedInstallmentModificationException
        extends RuntimeException {

    public PublishedInstallmentModificationException() {
        super(
                "Published installments cannot be modified."
        );
    }
}