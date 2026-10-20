package metopa.publication;

import metopa.catalog.CatalogService;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkDetails;
import metopa.catalog.WorkReference;
import metopa.catalog.WorkSummary;
import metopa.catalog.WorkType;
import metopa.media.BinaryStorage;
import metopa.publication.internal.Installment;
import metopa.publication.internal.InstallmentRepository;
import metopa.publication.internal.Page;
import metopa.publication.internal.PageRepository;
import metopa.catalog.WorkCoverContent;
import metopa.catalog.Genre;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTests {

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private CatalogService catalogService;

    @Mock
    private BinaryStorage binaryStorage;

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
                binaryStorage.store(
                        "installments",
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

        verify(binaryStorage)
                .store(
                        "installments",
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
                binaryStorage,
                never()
        ).store(
                any(String.class),
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
                binaryStorage,
                never()
        ).store(
                any(String.class),
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
                binaryStorage,
                never()
        ).store(
                any(String.class),
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

        when(
                pageRepository.existsByInstallmentId(
                        installment.getId()
                )
        ).thenReturn(true);

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

        when(
                pageRepository.existsByInstallmentId(
                        installment.getId()
                )
        ).thenReturn(false);

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

        verify(
                binaryStorage,
                never()
        ).store(
                any(String.class),
                any(UUID.class),
                any(byte[].class)
        );

        verify(
                pageRepository,
                never()
        ).save(any());
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

        Page firstPage =
                new Page(
                        installment.getId(),
                        1,
                        "installments/example/page-1",
                        "image/jpeg"
                );

        Page secondPage =
                new Page(
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
                List.of(
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
                binaryStorage.read(
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

        verify(binaryStorage)
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
                binaryStorage,
                never()
        ).read(any(String.class));
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
                binaryStorage,
                never()
        ).read(any(String.class));
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

    @Test
    void shouldFindPublishedWork() {
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        Installment installment =
                mock(Installment.class);

        when(installment.getId())
                .thenReturn(installmentId);

        when(installment.getType())
                .thenReturn(InstallmentType.ISSUE);

        when(installment.getNumber())
                .thenReturn(1);

        when(installment.getTitle())
                .thenReturn("Issue One");

        when(
                installmentRepository
                        .findByWorkIdAndStatusOrderByNumberAscCreatedAtAsc(
                                workId,
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(
                List.of(installment)
        );

        when(
                catalogService.findWorkDetails(workId)
        ).thenReturn(
                new WorkDetails(
                        workId,
                        "Metopa Origins",
                        "A science fiction graphic novel.",
                        WorkType.GRAPHIC_NOVEL,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE,
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        ),
                        true
                )
        );

        PublishedWorkView result =
                publicationService.findPublishedWork(
                        workId
                );

        assertThat(result.hasCover())
                .isTrue();

        assertThat(result.id())
                .isEqualTo(workId);

        assertThat(result.title())
                .isEqualTo("Metopa Origins");

        assertThat(result.description())
                .isEqualTo(
                        "A science fiction graphic novel."
                );

        assertThat(result.type())
                .isEqualTo(
                        WorkType.GRAPHIC_NOVEL
                );

        assertThat(result.readingDirection())
                .isEqualTo(
                        ReadingDirection.LEFT_TO_RIGHT
                );

        assertThat(result.presentationMode())
                .isEqualTo(
                        PresentationMode.SINGLE_PAGE
                );

        assertThat(result.genres())
                .containsExactlyInAnyOrder(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
                );

        assertThat(result.installments())
                .hasSize(1);

        PublishedInstallmentSummary summary =
                result.installments().getFirst();

        assertThat(summary.id())
                .isEqualTo(installmentId);

        assertThat(summary.type())
                .isEqualTo(InstallmentType.ISSUE);

        assertThat(summary.number())
                .isEqualTo(1);

        assertThat(summary.title())
                .isEqualTo("Issue One");
    }

    @Test
    void shouldRejectWorkWithoutPublishedContent() {
        UUID workId = UUID.randomUUID();

        when(
                installmentRepository
                        .findByWorkIdAndStatusOrderByNumberAscCreatedAtAsc(
                                workId,
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(List.of());

        assertThatThrownBy(() ->
                publicationService.findPublishedWork(
                        workId
                )
        ).isInstanceOf(
                PublishedWorkNotFoundException.class
        );

        verify(
                catalogService,
                never()
        ).findWorkDetails(workId);
    }

    @Test
    void shouldReturnPublishedInstallmentsForWork() {
        UUID workId = UUID.randomUUID();

        Installment first =
                mock(Installment.class);

        Installment second =
                mock(Installment.class);

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(first.getId())
                .thenReturn(firstId);

        when(first.getType())
                .thenReturn(InstallmentType.ISSUE);

        when(first.getNumber())
                .thenReturn(1);

        when(first.getTitle())
                .thenReturn("The Beginning");

        when(second.getId())
                .thenReturn(secondId);

        when(second.getType())
                .thenReturn(InstallmentType.ISSUE);

        when(second.getNumber())
                .thenReturn(2);

        when(second.getTitle())
                .thenReturn(null);

        when(
                installmentRepository
                        .findByWorkIdAndStatusOrderByNumberAscCreatedAtAsc(
                                workId,
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(
                List.of(first, second)
        );

        List<PublishedInstallmentSummary> result =
                publicationService
                        .findPublishedInstallments(workId);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).id())
                .isEqualTo(firstId);

        assertThat(result.get(0).type())
                .isEqualTo(InstallmentType.ISSUE);

        assertThat(result.get(0).number())
                .isEqualTo(1);

        assertThat(result.get(0).title())
                .isEqualTo("The Beginning");

        assertThat(result.get(1).id())
                .isEqualTo(secondId);

        assertThat(result.get(1).number())
                .isEqualTo(2);

        assertThat(result.get(1).title())
                .isNull();
    }

    @Test
    void shouldFindPublishedWorks() {
        UUID firstWorkId = UUID.randomUUID();
        UUID secondWorkId = UUID.randomUUID();

        Installment newestFirstWorkInstallment =
                mock(Installment.class);

        Installment olderFirstWorkInstallment =
                mock(Installment.class);

        Installment secondWorkInstallment =
                mock(Installment.class);

        when(newestFirstWorkInstallment.getWorkId())
                .thenReturn(firstWorkId);

        when(olderFirstWorkInstallment.getWorkId())
                .thenReturn(firstWorkId);

        when(secondWorkInstallment.getWorkId())
                .thenReturn(secondWorkId);

        when(
                installmentRepository
                        .findByStatusOrderByUpdatedAtDesc(
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(
                List.of(
                        newestFirstWorkInstallment,
                        olderFirstWorkInstallment,
                        secondWorkInstallment
                )
        );

        when(catalogService.findWorkSummary(firstWorkId))
                .thenReturn(
                        new WorkSummary(
                                firstWorkId,
                                "Metopa Origins",
                                WorkType.GRAPHIC_NOVEL,
                                Set.of(
                                        Genre.SCI_FI,
                                        Genre.ADVENTURE
                                ),
                                true
                        )
                );

        when(catalogService.findWorkSummary(secondWorkId))
                .thenReturn(
                        new WorkSummary(
                                secondWorkId,
                                "Another Work",
                                WorkType.COMIC,
                                Set.of(
                                        Genre.DRAMA
                                ),
                                false
                        )
                );

        List<PublishedWorkSummary> result =
                publicationService.findPublishedWorks();

        assertThat(result.get(0).hasCover())
                .isTrue();

        assertThat(result.get(1).hasCover())
                .isFalse();

        assertThat(result).hasSize(2);

        assertThat(result.get(0).id())
                .isEqualTo(firstWorkId);

        assertThat(result.get(0).title())
                .isEqualTo("Metopa Origins");

        assertThat(result.get(0).type())
                .isEqualTo(
                        WorkType.GRAPHIC_NOVEL
                );

        assertThat(result.get(0).genres())
                .containsExactlyInAnyOrder(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
                );

        assertThat(result.get(1).id())
                .isEqualTo(secondWorkId);

        assertThat(result.get(1).title())
                .isEqualTo("Another Work");

        assertThat(result.get(1).genres())
                .containsExactly(
                        Genre.DRAMA
                );
    }

    @Test
    void shouldReadCoverForPublishedWork() {
        UUID workId = UUID.randomUUID();

        byte[] content =
                "cover-content".getBytes();

        when(
                installmentRepository
                        .existsByWorkIdAndStatus(
                                workId,
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(true);

        when(
                catalogService.findWorkCover(workId)
        ).thenReturn(
                new WorkCoverContent(
                        "image/webp",
                        content
                )
        );

        PublishedWorkCoverContent result =
                publicationService
                        .findPublishedWorkCover(
                                workId
                        );

        assertThat(result.contentType())
                .isEqualTo("image/webp");

        assertThat(result.content())
                .isEqualTo(content);
    }

    @Test
    void shouldHideCoverForWorkWithoutPublishedContent() {
        UUID workId = UUID.randomUUID();

        when(
                installmentRepository
                        .existsByWorkIdAndStatus(
                                workId,
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(false);

        assertThatThrownBy(() ->
                publicationService
                        .findPublishedWorkCover(
                                workId
                        )
        ).isInstanceOf(
                PublishedWorkCoverNotFoundException.class
        );

        verify(
                catalogService,
                never()
        ).findWorkCover(workId);
    }

    @Test
    void shouldFilterPublishedWorksByGenre() {
        UUID scienceFictionWorkId =
                UUID.randomUUID();

        UUID dramaWorkId =
                UUID.randomUUID();

        Installment scienceFictionInstallment =
                mock(Installment.class);

        Installment dramaInstallment =
                mock(Installment.class);

        when(scienceFictionInstallment.getWorkId())
                .thenReturn(scienceFictionWorkId);

        when(dramaInstallment.getWorkId())
                .thenReturn(dramaWorkId);

        when(
                installmentRepository
                        .findByStatusOrderByUpdatedAtDesc(
                                PublicationStatus.PUBLISHED
                        )
        ).thenReturn(
                List.of(
                        scienceFictionInstallment,
                        dramaInstallment
                )
        );

        when(
                catalogService.findWorkSummary(
                        scienceFictionWorkId
                )
        ).thenReturn(
                new WorkSummary(
                        scienceFictionWorkId,
                        "Metopa Origins",
                        WorkType.GRAPHIC_NOVEL,
                        Set.of(
                                Genre.SCI_FI,
                                Genre.ADVENTURE
                        ),
                        true
                )
        );

        when(
                catalogService.findWorkSummary(
                        dramaWorkId
                )
        ).thenReturn(
                new WorkSummary(
                        dramaWorkId,
                        "Another Work",
                        WorkType.COMIC,
                        Set.of(
                                Genre.DRAMA
                        ),
                        false
                )
        );

        List<PublishedWorkSummary> result =
                publicationService
                        .findPublishedWorks(
                                Genre.SCI_FI
                        );

        assertThat(result)
                .hasSize(1);

        assertThat(result.getFirst().id())
                .isEqualTo(
                        scienceFictionWorkId
                );

        assertThat(result.getFirst().genres())
                .containsExactlyInAnyOrder(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
                );
    }

    @Test
    void shouldUpdateDraftInstallmentMetadataForOwner() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Original Title"
                );

        UpdateInstallmentCommand command =
                new UpdateInstallmentCommand(
                        InstallmentType.ISSUE,
                        2,
                        "  Updated Title  "
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
                installmentRepository
                        .existsByWorkIdAndTypeAndNumber(
                                workId,
                                InstallmentType.ISSUE,
                                2
                        )
        ).thenReturn(false);

        publicationService.updateInstallment(
                userId,
                installment.getId(),
                command
        );

        assertThat(installment.getType())
                .isEqualTo(
                        InstallmentType.ISSUE
                );

        assertThat(installment.getNumber())
                .isEqualTo(2);

        assertThat(installment.getTitle())
                .isEqualTo("Updated Title");

        verify(installmentRepository)
                .existsByWorkIdAndTypeAndNumber(
                        workId,
                        InstallmentType.ISSUE,
                        2
                );
    }

    @Test
    void shouldUpdateOnlyInstallmentTitleWithoutDuplicateCheck() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Original Title"
                );

        UpdateInstallmentCommand command =
                new UpdateInstallmentCommand(
                        InstallmentType.CHAPTER,
                        1,
                        "Updated Title"
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

        publicationService.updateInstallment(
                userId,
                installment.getId(),
                command
        );

        assertThat(installment.getType())
                .isEqualTo(
                        InstallmentType.CHAPTER
                );

        assertThat(installment.getNumber())
                .isEqualTo(1);

        assertThat(installment.getTitle())
                .isEqualTo("Updated Title");

        verify(
                installmentRepository,
                never()
        ).existsByWorkIdAndTypeAndNumber(
                any(UUID.class),
                any(InstallmentType.class),
                anyInt()
        );
    }

    @Test
    void shouldRejectDuplicateInstallmentMetadata() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Original Title"
                );

        UpdateInstallmentCommand command =
                new UpdateInstallmentCommand(
                        InstallmentType.ISSUE,
                        2,
                        "Updated Title"
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
                installmentRepository
                        .existsByWorkIdAndTypeAndNumber(
                                workId,
                                InstallmentType.ISSUE,
                                2
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                publicationService.updateInstallment(
                        userId,
                        installment.getId(),
                        command
                )
        ).isInstanceOf(
                InstallmentAlreadyExistsException.class
        );

        assertThat(installment.getType())
                .isEqualTo(
                        InstallmentType.CHAPTER
                );

        assertThat(installment.getNumber())
                .isEqualTo(1);

        assertThat(installment.getTitle())
                .isEqualTo("Original Title");
    }

    @Test
    void shouldRejectMetadataUpdateForPublishedInstallment() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Published Title"
                );

        installment.publish();

        UpdateInstallmentCommand command =
                new UpdateInstallmentCommand(
                        InstallmentType.ISSUE,
                        2,
                        "Updated Title"
                );

        when(
                installmentRepository.findById(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        assertThatThrownBy(() ->
                publicationService.updateInstallment(
                        userId,
                        installment.getId(),
                        command
                )
        ).isInstanceOf(
                PublishedInstallmentModificationException.class
        );

        assertThat(installment.getType())
                .isEqualTo(
                        InstallmentType.CHAPTER
                );

        assertThat(installment.getNumber())
                .isEqualTo(1);

        assertThat(installment.getTitle())
                .isEqualTo("Published Title");

        verify(
                catalogService,
                never()
        ).findWork(any(UUID.class));

        verify(
                installmentRepository,
                never()
        ).existsByWorkIdAndTypeAndNumber(
                any(UUID.class),
                any(InstallmentType.class),
                anyInt()
        );
    }

    @Test
    void shouldFindCreatorInstallmentsForWorkOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment firstInstallment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Chapter One"
                );

        firstInstallment.publish();

        Installment secondInstallment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        2,
                        "Chapter Two"
                );

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                ownerId
                        )
                );

        when(
                installmentRepository
                        .findByWorkIdOrderByNumberAscCreatedAtAsc(
                                workId
                        )
        ).thenReturn(
                List.of(
                        firstInstallment,
                        secondInstallment
                )
        );

        List<CreatorInstallmentSummary> installments =
                publicationService.findCreatorInstallments(
                        ownerId,
                        workId
                );

        assertThat(installments)
                .hasSize(2);

        assertThat(installments.get(0).id())
                .isEqualTo(
                        firstInstallment.getId()
                );

        assertThat(installments.get(0).type())
                .isEqualTo(
                        InstallmentType.CHAPTER
                );

        assertThat(installments.get(0).number())
                .isEqualTo(1);

        assertThat(installments.get(0).title())
                .isEqualTo("Chapter One");

        assertThat(installments.get(0).status())
                .isEqualTo(
                        PublicationStatus.PUBLISHED
                );

        assertThat(installments.get(1).id())
                .isEqualTo(
                        secondInstallment.getId()
                );

        assertThat(installments.get(1).number())
                .isEqualTo(2);

        assertThat(installments.get(1).title())
                .isEqualTo("Chapter Two");

        assertThat(installments.get(1).status())
                .isEqualTo(
                        PublicationStatus.DRAFT
                );

        verify(catalogService)
                .findWork(workId);

        verify(installmentRepository)
                .findByWorkIdOrderByNumberAscCreatedAtAsc(
                        workId
                );
    }

    @Test
    void shouldRejectCreatorInstallmentListForUserWhoDoesNotOwnWork() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                ownerId
                        )
                );

        assertThatThrownBy(() ->
                publicationService.findCreatorInstallments(
                        anotherUserId,
                        workId
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(catalogService)
                .findWork(workId);

        verify(
                installmentRepository,
                never()
        ).findByWorkIdOrderByNumberAscCreatedAtAsc(
                workId
        );
    }

    @Test
    void shouldFindCreatorInstallmentForWorkOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Chapter One"
                );

        Page firstPage =
                new Page(
                        installment.getId(),
                        1,
                        "installments/example/page-1",
                        "image/jpeg"
                );

        Page secondPage =
                new Page(
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

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(
                                workId,
                                ownerId
                        )
                );

        when(
                pageRepository
                        .findByInstallmentIdOrderByNumberAsc(
                                installment.getId()
                        )
        ).thenReturn(
                List.of(
                        firstPage,
                        secondPage
                )
        );

        CreatorInstallmentView result =
                publicationService
                        .findCreatorInstallment(
                                ownerId,
                                installment.getId()
                        );

        assertThat(result.id())
                .isEqualTo(
                        installment.getId()
                );

        assertThat(result.workId())
                .isEqualTo(workId);

        assertThat(result.type())
                .isEqualTo(
                        InstallmentType.CHAPTER
                );

        assertThat(result.number())
                .isEqualTo(1);

        assertThat(result.title())
                .isEqualTo("Chapter One");

        assertThat(result.status())
                .isEqualTo(
                        PublicationStatus.DRAFT
                );

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

        assertThat(result.pages().get(0).id())
                .isEqualTo(
                        firstPage.getId()
                );

        assertThat(result.pages().get(1).id())
                .isEqualTo(
                        secondPage.getId()
                );

        verify(catalogService)
                .findWork(workId);

        verify(pageRepository)
                .findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                );
    }

    @Test
    void shouldRejectCreatorInstallmentForUserWhoDoesNotOwnWork() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment =
                new Installment(
                        workId,
                        InstallmentType.CHAPTER,
                        1,
                        "Private Draft"
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
                publicationService
                        .findCreatorInstallment(
                                anotherUserId,
                                installment.getId()
                        )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(catalogService)
                .findWork(workId);

        verify(
                pageRepository,
                never()
        ).findByInstallmentIdOrderByNumberAsc(
                installment.getId()
        );
    }

    @Test
    void shouldReadCreatorPageContentForWorkOwner() {
        UUID ownerId = UUID.randomUUID();
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

        byte[] content =
                "page-content".getBytes();

        when(
                pageRepository.findById(
                        page.getId()
                )
        ).thenReturn(
                Optional.of(page)
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

        when(
                binaryStorage.read(
                        page.getStorageKey()
                )
        ).thenReturn(content);

        CreatorPageContent result =
                publicationService
                        .findCreatorPageContent(
                                ownerId,
                                page.getId()
                        );

        assertThat(installment.getStatus())
                .isEqualTo(
                        PublicationStatus.DRAFT
                );

        assertThat(result.contentType())
                .isEqualTo("image/jpeg");

        assertThat(result.content())
                .isEqualTo(content);

        verify(catalogService)
                .findWork(workId);

        verify(binaryStorage)
                .read(
                        page.getStorageKey()
                );
    }

    @Test
    void shouldRejectCreatorPageContentForUserWhoDoesNotOwnWork() {
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

        Page page =
                new Page(
                        installment.getId(),
                        1,
                        "installments/example/private-page",
                        "image/jpeg"
                );

        when(
                pageRepository.findById(
                        page.getId()
                )
        ).thenReturn(
                Optional.of(page)
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
                publicationService
                        .findCreatorPageContent(
                                anotherUserId,
                                page.getId()
                        )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(catalogService)
                .findWork(workId);

        verify(
                binaryStorage,
                never()
        ).read(
                any(String.class)
        );
    }
}