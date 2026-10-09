package metopa.publication;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkReference;
import metopa.publication.internal.Installment;
import metopa.publication.internal.InstallmentRepository;
import metopa.publication.internal.Page;
import metopa.publication.internal.PageRepository;
import metopa.publication.internal.storage.PageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTests {

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private CatalogService catalogService;

    @Mock
    private PageStorage pageStorage;

    @InjectMocks
    private PublicationService publicationService;

    @Test
    void shouldCreateInstallmentForWorkOwner() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "The Beginning"
                );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                userId
                        )
                );

        when(
                installmentRepository
                        .existsByWorkIdAndTypeAndNumber(
                                workId,
                                InstallmentType.CHAPTER,
                                1
                        )
        ).thenReturn(false);

        when(
                installmentRepository.save(
                        any(Installment.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        UUID installmentId =
                publicationService.createInstallment(
                        userId,
                        command
                );

        assertThat(installmentId).isNotNull();

        verify(installmentRepository)
                .save(any(Installment.class));
    }

    @Test
    void shouldRejectUserWhoDoesNotOwnWork() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        workId,
                        InstallmentType.ISSUE,
                        1,
                        null
                );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                ownerId
                        )
                );

        assertThatThrownBy(() ->
                publicationService.createInstallment(
                        anotherUserId,
                        command
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(
                installmentRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldRejectDuplicateInstallment() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                userId
                        )
                );

        when(
                installmentRepository
                        .existsByWorkIdAndTypeAndNumber(
                                workId,
                                InstallmentType.CHAPTER,
                                1
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                publicationService.createInstallment(
                        userId,
                        command
                )
        ).isInstanceOf(
                InstallmentAlreadyExistsException.class
        );

        verify(
                installmentRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldCreatePageForWorkOwner() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        byte[] content =
                "page-content".getBytes();

        CreatePageCommand command =
                new CreatePageCommand(
                        installment.getId(),
                        1,
                        "image/jpeg",
                        content
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                userId
                        )
                );

        when(
                pageRepository
                        .existsByInstallmentIdAndNumber(
                                installment.getId(),
                                1
                        )
        ).thenReturn(false);

        when(
                pageStorage.store(
                        installment.getId(),
                        content
                )
        ).thenReturn(
                "installments/"
                        + installment.getId()
                        + "/page-file"
        );

        when(
                pageRepository.save(
                        any(Page.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        UUID pageId =
                publicationService.createPage(
                        userId,
                        command
                );

        assertThat(pageId).isNotNull();

        verify(pageStorage)
                .store(
                        installment.getId(),
                        content
                );

        verify(pageRepository)
                .save(any(Page.class));
    }

    @Test
    void shouldRejectPageForUnknownInstallment() {
        UUID userId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        byte[] content =
                "page-content".getBytes();

        CreatePageCommand command =
                new CreatePageCommand(
                        installmentId,
                        1,
                        "image/jpeg",
                        content
                );

        when(
                installmentRepository.findById(
                        installmentId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                publicationService.createPage(
                        userId,
                        command
                )
        ).isInstanceOf(
                InstallmentNotFoundException.class
        );

        verify(
                pageStorage,
                never()
        ).store(
                any(UUID.class),
                any(byte[].class)
        );

        verify(
                pageRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldRejectPageFromUserWhoDoesNotOwnWork() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        byte[] content =
                "page-content".getBytes();

        CreatePageCommand command =
                new CreatePageCommand(
                        installment.getId(),
                        1,
                        "image/jpeg",
                        content
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                ownerId
                        )
                );

        assertThatThrownBy(() ->
                publicationService.createPage(
                        anotherUserId,
                        command
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(
                pageStorage,
                never()
        ).store(
                any(UUID.class),
                any(byte[].class)
        );

        verify(
                pageRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldRejectDuplicatePageNumber() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        byte[] content =
                "page-content".getBytes();

        CreatePageCommand command =
                new CreatePageCommand(
                        installment.getId(),
                        1,
                        "image/jpeg",
                        content
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                userId
                        )
                );

        when(
                pageRepository
                        .existsByInstallmentIdAndNumber(
                                installment.getId(),
                                1
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                publicationService.createPage(
                        userId,
                        command
                )
        ).isInstanceOf(
                PageAlreadyExistsException.class
        );

        verify(
                pageStorage,
                never()
        ).store(
                any(UUID.class),
                any(byte[].class)
        );

        verify(
                pageRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldPublishInstallmentWithPages() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                userId
                        )
                );

        when(pageRepository.existsByInstallmentId(
                installment.getId()
        )).thenReturn(true);

        publicationService.publishInstallment(
                userId,
                installment.getId()
        );

        assertThat(installment.getStatus())
                .isEqualTo(
                        PublicationStatus.PUBLISHED
                );
    }

    @Test
    void shouldRejectPublishingInstallmentWithoutPages() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                userId
                        )
                );

        when(pageRepository.existsByInstallmentId(
                installment.getId()
        )).thenReturn(false);

        assertThatThrownBy(() ->
                publicationService.publishInstallment(
                        userId,
                        installment.getId()
                )
        ).isInstanceOf(
                InstallmentHasNoPagesException.class
        );

        assertThat(installment.getStatus())
                .isEqualTo(
                        PublicationStatus.DRAFT
                );
    }

    @Test
    void shouldRejectPageForPublishedInstallment() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        installment.publish();

        CreatePageCommand command =
                new CreatePageCommand(
                        installment.getId(),
                        2,
                        "image/jpeg",
                        "page-content".getBytes()
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        assertThatThrownBy(() ->
                publicationService.createPage(
                        userId,
                        command
                )
        ).isInstanceOf(
                PublishedInstallmentModificationException.class
        );

        verify(pageStorage, never())
                .store(
                        any(UUID.class),
                        any(byte[].class)
                );

        verify(pageRepository, never())
                .save(any());
    }

    @Test
    void shouldFindPublishedInstallmentWithOrderedPages() {
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "The Beginning"
                );

        installment.publish();

        Page firstPage = new Page(
                installment.getId(),
                1,
                "installments/example/page-1",
                "image/jpeg"
        );

        Page secondPage = new Page(
                installment.getId(),
                2,
                "installments/example/page-2",
                "image/webp"
        );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        when(
                pageRepository
                        .findByInstallmentIdOrderByNumberAsc(
                                installment.getId()
                        )
        ).thenReturn(
                java.util.List.of(
                        firstPage,
                        secondPage
                )
        );

        PublishedInstallmentView result =
                publicationService
                        .findPublishedInstallment(
                                installment.getId()
                        );

        assertThat(result.id())
                .isEqualTo(installment.getId());

        assertThat(result.workId())
                .isEqualTo(workId);

        assertThat(result.type())
                .isEqualTo(
                        InstallmentType.CHAPTER
                );

        assertThat(result.number())
                .isEqualTo(1);

        assertThat(result.title())
                .isEqualTo("The Beginning");

        assertThat(result.pages())
                .hasSize(2);

        assertThat(result.pages())
                .extracting(PageView::number)
                .containsExactly(1, 2);

        assertThat(result.pages())
                .extracting(PageView::contentType)
                .containsExactly(
                        "image/jpeg",
                        "image/webp"
                );
    }

    @Test
    void shouldHideDraftInstallmentFromPublicReading() {
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        assertThatThrownBy(() ->
                publicationService
                        .findPublishedInstallment(
                                installment.getId()
                        )
        ).isInstanceOf(
                PublishedInstallmentNotFoundException.class
        );

        verify(
                pageRepository,
                never()
        ).findByInstallmentIdOrderByNumberAsc(
                any(UUID.class)
        );
    }

    @Test
    void shouldRejectUnknownPublishedInstallment() {
        UUID installmentId =
                UUID.randomUUID();

        when(
                installmentRepository.findById(
                        installmentId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                publicationService
                        .findPublishedInstallment(
                                installmentId
                        )
        ).isInstanceOf(
                PublishedInstallmentNotFoundException.class
        );

        verify(
                pageRepository,
                never()
        ).findByInstallmentIdOrderByNumberAsc(
                any(UUID.class)
        );
    }

    @Test
    void shouldReadPageContentFromPublishedInstallment() {
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        installment.publish();

        Page page =
                new Page(
                        installment.getId(),
                        1,
                        "installments/example/page-file",
                        "image/jpeg"
                );

        byte[] content =
                "page-content".getBytes();

        when(
                pageRepository.findById(
                        page.getId()
                )
        ).thenReturn(Optional.of(page));

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(
                pageStorage.read(
                        page.getStorageKey()
                )
        ).thenReturn(content);

        PublishedPageContent result =
                publicationService
                        .findPublishedPageContent(
                                page.getId()
                        );

        assertThat(result.contentType())
                .isEqualTo("image/jpeg");

        assertThat(result.content())
                .isEqualTo(content);

        verify(pageStorage)
                .read(page.getStorageKey());
    }

    @Test
    void shouldHidePageFromDraftInstallment() {
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        null
                );

        Page page =
                new Page(
                        installment.getId(),
                        1,
                        "installments/example/page-file",
                        "image/jpeg"
                );

        when(
                pageRepository.findById(
                        page.getId()
                )
        ).thenReturn(Optional.of(page));

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        assertThatThrownBy(() ->
                publicationService
                        .findPublishedPageContent(
                                page.getId()
                        )
        ).isInstanceOf(
                PublishedPageNotFoundException.class
        );

        verify(
                pageStorage,
                never()
        ).read(any());
    }

    @Test
    void shouldRejectUnknownPublishedPage() {
        UUID pageId = UUID.randomUUID();

        when(
                pageRepository.findById(pageId)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                publicationService
                        .findPublishedPageContent(
                                pageId
                        )
        ).isInstanceOf(
                PublishedPageNotFoundException.class
        );

        verify(
                pageStorage,
                never()
        ).read(any());
    }

    @Test
    void shouldReportPublishedContentForWork() {
        UUID workId = UUID.randomUUID();

        when(
                installmentRepository.existsByWorkIdAndStatus(
                        workId,
                        PublicationStatus.PUBLISHED
                )
        ).thenReturn(true);

        boolean result =
                publicationService.hasPublishedContent(workId);

        assertThat(result).isTrue();
    }

    @Test
    void shouldFindPublishedPageReference() {
        UUID pageId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Page page = mock(Page.class);
        Installment installment =
                mock(Installment.class);

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        when(page.getId())
                .thenReturn(pageId);

        when(page.getInstallmentId())
                .thenReturn(installmentId);

        when(
                installmentRepository.findById(
                        installmentId
                )
        ).thenReturn(
                Optional.of(installment)
        );

        when(installment.getId())
                .thenReturn(installmentId);

        when(installment.getWorkId())
                .thenReturn(workId);

        when(installment.getStatus())
                .thenReturn(
                        PublicationStatus.PUBLISHED
                );

        PublishedPageReference reference =
                publicationService
                        .findPublishedPageReference(
                                pageId
                        );

        assertThat(reference.pageId())
                .isEqualTo(pageId);

        assertThat(reference.installmentId())
                .isEqualTo(installmentId);

        assertThat(reference.workId())
                .isEqualTo(workId);
    }

}