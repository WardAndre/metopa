package metopa.publication;

import jakarta.validation.Valid;
import metopa.catalog.CatalogService;
import metopa.catalog.WorkReference;
import metopa.catalog.WorkDetails;
import metopa.publication.internal.Installment;
import metopa.publication.internal.InstallmentRepository;
import metopa.publication.internal.Page;
import metopa.publication.internal.PageRepository;
import metopa.publication.internal.storage.PageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
public class PublicationService {

    private final InstallmentRepository installmentRepository;
    private final PageRepository pageRepository;
    private final CatalogService catalogService;
    private final PageStorage pageStorage;

    public PublicationService(
            InstallmentRepository installmentRepository,
            PageRepository pageRepository,
            CatalogService catalogService,
            PageStorage pageStorage
    ) {
        this.installmentRepository = installmentRepository;
        this.pageRepository = pageRepository;
        this.catalogService = catalogService;
        this.pageStorage = pageStorage;
    }

    @Transactional
    public UUID createInstallment(
            UUID userId,
            @Valid CreateInstallmentCommand command
    ) {
        WorkReference work =
                catalogService.findWork(command.workId());

        if (!work.ownerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        if (installmentRepository
                .existsByWorkIdAndTypeAndNumber(
                        command.workId(),
                        command.type(),
                        command.number()
                )) {
            throw new InstallmentAlreadyExistsException();
        }

        Installment installment = new Installment(
                command.workId(),
                command.type(),
                command.number(),
                command.title()
        );

        Installment saved =
                installmentRepository.save(installment);

        return saved.getId();
    }

    @Transactional
    public UUID createPage(
            UUID userId,
            @Valid CreatePageCommand command
    ) {
        Installment installment =
                installmentRepository
                        .findById(command.installmentId())
                        .orElseThrow(() ->
                                new InstallmentNotFoundException(
                                        command.installmentId()
                                )
                        );

        if (installment.getStatus()
                == PublicationStatus.PUBLISHED) {
            throw new PublishedInstallmentModificationException();
        }

        WorkReference work =
                catalogService.findWork(
                        installment.getWorkId()
                );

        if (!work.ownerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        if (pageRepository.existsByInstallmentIdAndNumber(
                command.installmentId(),
                command.number()
        )) {
            throw new PageAlreadyExistsException();
        }

        String storageKey = pageStorage.store(
                command.installmentId(),
                command.content()
        );

        Page page = new Page(
                command.installmentId(),
                command.number(),
                storageKey,
                command.contentType()
        );

        Page saved =
                pageRepository.save(page);

        return saved.getId();
    }

    @Transactional
    public void publishInstallment(
            UUID userId,
            UUID installmentId
    ) {
        Installment installment =
                installmentRepository
                        .findById(installmentId)
                        .orElseThrow(() ->
                                new InstallmentNotFoundException(
                                        installmentId
                                )
                        );

        WorkReference work =
                catalogService.findWork(
                        installment.getWorkId()
                );

        if (!work.ownerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        if (!pageRepository.existsByInstallmentId(
                installmentId
        )) {
            throw new InstallmentHasNoPagesException();
        }

        installment.publish();
    }

    @Transactional(readOnly = true)
    public PublishedInstallmentView findPublishedInstallment(
            UUID installmentId
    ) {
        Installment installment =
                installmentRepository
                        .findById(installmentId)
                        .orElseThrow(() ->
                                new PublishedInstallmentNotFoundException(
                                        installmentId
                                )
                        );

        if (installment.getStatus()
                != PublicationStatus.PUBLISHED) {
            throw new PublishedInstallmentNotFoundException(
                    installmentId
            );
        }

        var pages =
                pageRepository
                        .findByInstallmentIdOrderByNumberAsc(
                                installmentId
                        )
                        .stream()
                        .map(page ->
                                new PageView(
                                        page.getId(),
                                        page.getNumber(),
                                        page.getContentType()
                                )
                        )
                        .toList();

        return new PublishedInstallmentView(
                installment.getId(),
                installment.getWorkId(),
                installment.getType(),
                installment.getNumber(),
                installment.getTitle(),
                pages
        );
    }

    @Transactional(readOnly = true)
    public PublishedPageContent findPublishedPageContent(
            UUID pageId
    ) {
        Page page =
                pageRepository
                        .findById(pageId)
                        .orElseThrow(() ->
                                new PublishedPageNotFoundException(
                                        pageId
                                )
                        );

        Installment installment =
                installmentRepository
                        .findById(page.getInstallmentId())
                        .orElseThrow(() ->
                                new PublishedPageNotFoundException(
                                        pageId
                                )
                        );

        if (installment.getStatus()
                != PublicationStatus.PUBLISHED) {
            throw new PublishedPageNotFoundException(
                    pageId
            );
        }

        byte[] content =
                pageStorage.read(
                        page.getStorageKey()
                );

        return new PublishedPageContent(
                page.getContentType(),
                content
        );
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedContent(UUID workId) {
        return installmentRepository
                .existsByWorkIdAndStatus(
                        workId,
                        PublicationStatus.PUBLISHED
                );
    }

    @Transactional(readOnly = true)
    public PublishedPageReference findPublishedPageReference(
            UUID pageId
    ) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() ->
                        new PublishedPageNotFoundException(pageId)
                );

        Installment installment =
                installmentRepository
                        .findById(
                                page.getInstallmentId()
                        )
                        .orElseThrow(() ->
                                new PublishedPageNotFoundException(pageId)
                        );

        if (installment.getStatus()
                != PublicationStatus.PUBLISHED) {
            throw new PublishedPageNotFoundException(pageId);
        }

        return new PublishedPageReference(
                page.getId(),
                installment.getId(),
                installment.getWorkId()
        );
    }

    @Transactional(readOnly = true)
    public PublishedWorkView findPublishedWork(
            UUID workId
    ) {
        List<PublishedInstallmentSummary> installments =
                findPublishedInstallments(workId);

        if (installments.isEmpty()) {
            throw new PublishedWorkNotFoundException(
                    workId
            );
        }

        WorkDetails work =
                catalogService.findWorkDetails(
                        workId
                );

        return new PublishedWorkView(
                work.id(),
                work.title(),
                work.description(),
                work.type(),
                work.readingDirection(),
                work.presentationMode(),
                installments
        );
    }

    @Transactional(readOnly = true)
    public List<PublishedInstallmentSummary> findPublishedInstallments(
            UUID workId
    ) {
        return installmentRepository
                .findByWorkIdAndStatusOrderByNumberAscCreatedAtAsc(
                        workId,
                        PublicationStatus.PUBLISHED
                )
                .stream()
                .map(installment ->
                        new PublishedInstallmentSummary(
                                installment.getId(),
                                installment.getType(),
                                installment.getNumber(),
                                installment.getTitle()
                        )
                )
                .toList();
    }
}