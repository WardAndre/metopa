package metopa.publication;

import jakarta.validation.Valid;
import metopa.catalog.CatalogService;
import metopa.catalog.WorkReference;
import metopa.catalog.WorkDetails;
import metopa.catalog.Genre;
import metopa.media.BinaryStorage;
import metopa.publication.internal.Installment;
import metopa.publication.internal.InstallmentRepository;
import metopa.publication.internal.Page;
import metopa.publication.internal.PageRepository;
import metopa.catalog.WorkCoverContent;
import metopa.catalog.WorkCoverNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Service
@Validated
public class PublicationService {

    private final InstallmentRepository installmentRepository;
    private final PageRepository pageRepository;
    private final CatalogService catalogService;
    private final BinaryStorage binaryStorage;

    public PublicationService(
            InstallmentRepository installmentRepository,
            PageRepository pageRepository,
            CatalogService catalogService,
            BinaryStorage binaryStorage
    ) {
        this.installmentRepository = installmentRepository;
        this.pageRepository = pageRepository;
        this.catalogService = catalogService;
        this.binaryStorage = binaryStorage;
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
                        .findByIdForUpdate(command.installmentId())
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

        String storageKey =
                binaryStorage.store(
                        "installments",
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
    public void deletePage(
            UUID userId,
            UUID installmentId,
            UUID pageId
    ) {
        Installment installment =
                installmentRepository
                        .findByIdForUpdate(installmentId)
                        .orElseThrow(() ->
                                new InstallmentNotFoundException(
                                        installmentId
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

        Page page =
                pageRepository
                        .findById(pageId)
                        .filter(candidate ->
                                candidate
                                        .getInstallmentId()
                                        .equals(installmentId)
                        )
                        .orElseThrow(() ->
                                new PageNotFoundException(pageId)
                        );

        String storageKey = page.getStorageKey();

        pageRepository.delete(page);
        pageRepository.flush();

        binaryStorage.delete(storageKey);
    }

    @Transactional
    public void publishInstallment(
            UUID userId,
            UUID installmentId
    ) {
        Installment installment =
                installmentRepository
                        .findByIdForUpdate(installmentId)
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
                binaryStorage.read(
                        page.getStorageKey()
                );

        return new PublishedPageContent(
                page.getContentType(),
                content
        );
    }

    @Transactional(readOnly = true)
    public CreatorPageContent findCreatorPageContent(
            UUID userId,
            UUID pageId
    ) {
        Page page =
                pageRepository
                        .findById(pageId)
                        .orElseThrow(() ->
                                new PageNotFoundException(
                                        pageId
                                )
                        );

        Installment installment =
                installmentRepository
                        .findById(
                                page.getInstallmentId()
                        )
                        .orElseThrow(() ->
                                new InstallmentNotFoundException(
                                        page.getInstallmentId()
                                )
                        );

        WorkReference work =
                catalogService.findWork(
                        installment.getWorkId()
                );

        if (!work.ownerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        byte[] content =
                binaryStorage.read(
                        page.getStorageKey()
                );

        return new CreatorPageContent(
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
                work.genres(),
                work.hasCover(),
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

    @Transactional(readOnly = true)
    public List<CreatorInstallmentSummary> findCreatorInstallments(
            UUID userId,
            UUID workId
    ) {
        WorkReference work =
                catalogService.findWork(
                        workId
                );

        if (!work.ownerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        return installmentRepository
                .findByWorkIdOrderByNumberAscCreatedAtAsc(
                        workId
                )
                .stream()
                .map(installment ->
                        new CreatorInstallmentSummary(
                                installment.getId(),
                                installment.getType(),
                                installment.getNumber(),
                                installment.getTitle(),
                                installment.getStatus()
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublishedWorkSummary> findPublishedWorks() {
        return findPublishedWorks(null);
    }

    @Transactional(readOnly = true)
    public List<PublishedWorkSummary> findPublishedWorks(
            Genre genre
    ) {
        return installmentRepository
                .findByStatusOrderByUpdatedAtDesc(
                        PublicationStatus.PUBLISHED
                )
                .stream()
                .map(Installment::getWorkId)
                .distinct()
                .map(catalogService::findWorkSummary)
                .filter(work ->
                        genre == null
                                || work.genres().contains(genre)
                )
                .map(work ->
                        new PublishedWorkSummary(
                                work.id(),
                                work.title(),
                                work.type(),
                                work.genres(),
                                work.hasCover()
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public PublishedWorkCoverContent findPublishedWorkCover(
            UUID workId
    ) {
        if (!hasPublishedContent(workId)) {
            throw new PublishedWorkCoverNotFoundException(
                    workId
            );
        }

        WorkCoverContent cover;

        try {
            cover = catalogService.findWorkCover(
                    workId
            );
        } catch (WorkCoverNotFoundException exception) {
            throw new PublishedWorkCoverNotFoundException(
                    workId
            );
        }

        return new PublishedWorkCoverContent(
                cover.contentType(),
                cover.content()
        );
    }

    @Transactional
    public void updateInstallment(
            UUID userId,
            UUID installmentId,
            @Valid UpdateInstallmentCommand command
    ) {
        Installment installment =
                installmentRepository
                        .findByIdForUpdate(installmentId)
                        .orElseThrow(() ->
                                new InstallmentNotFoundException(
                                        installmentId
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

        boolean identityChanged =
                installment.getType()
                        != command.type()
                        || installment.getNumber()
                        != command.number();

        if (identityChanged
                && installmentRepository
                .existsByWorkIdAndTypeAndNumber(
                        installment.getWorkId(),
                        command.type(),
                        command.number()
                )) {
            throw new InstallmentAlreadyExistsException();
        }

        installment.updateMetadata(
                command.type(),
                command.number(),
                command.title()
        );
    }

    @Transactional(readOnly = true)
    public CreatorInstallmentView findCreatorInstallment(
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

        List<PageView> pages =
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

        return new CreatorInstallmentView(
                installment.getId(),
                installment.getWorkId(),
                installment.getType(),
                installment.getNumber(),
                installment.getTitle(),
                installment.getStatus(),
                pages
        );
    }

    @Transactional
    public void reorderPages(
            UUID userId,
            UUID installmentId,
            @Valid ReorderPagesCommand command
    ) {
        Installment installment =
                installmentRepository
                        .findByIdForUpdate(installmentId)
                        .orElseThrow(() ->
                                new InstallmentNotFoundException(
                                        installmentId
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

        List<Page> pages =
                pageRepository
                        .findByInstallmentIdOrderByNumberAsc(
                                installmentId
                        );

        List<UUID> requestedIds = command.pageIds();

        if (requestedIds == null
                || requestedIds.isEmpty()
                || requestedIds.size() != pages.size()) {
            throw new InvalidPageOrderException();
        }

        Map<UUID, Page> pagesById =
                new HashMap<>();

        for (Page page : pages) {
            pagesById.put(
                    page.getId(),
                    page
            );
        }

        if (new HashSet<>(requestedIds).size()
                != requestedIds.size()
                || !pagesById.keySet()
                .containsAll(requestedIds)) {
            throw new InvalidPageOrderException();
        }

        int maxNumber =
                pages.stream()
                        .mapToInt(Page::getNumber)
                        .max()
                        .orElseThrow();

        if (maxNumber > Integer.MAX_VALUE - pages.size()) {
            throw new IllegalStateException(
                    "Cannot allocate temporary page numbers."
            );
        }

        // Phase 1: move pages to unused positive numbers.

        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).changeNumber(
                    maxNumber + i + 1
            );
        }

        pageRepository.flush();

        // Phase 2: assign the requested final order.

        for (int i = 0; i < requestedIds.size(); i++) {
            Page page =
                    pagesById.get(
                            requestedIds.get(i)
                    );

            page.changeNumber(i + 1);
        }
    }
}