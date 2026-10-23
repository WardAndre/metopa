package metopa.publication;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkReference;
import metopa.media.BinaryStorage;
import metopa.publication.internal.Installment;
import metopa.publication.internal.InstallmentRepository;
import metopa.publication.internal.Page;
import metopa.publication.internal.PageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageReorderingServiceTests {

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
    void shouldReorderPagesForDraftInstallmentOwner() {
        UUID ownerId = UUID.randomUUID();
        Installment installment = draftInstallment();

        Page first = page(installment, 1);
        Page second = page(installment, 2);
        Page third = page(installment, 3);

        givenInstallmentOwnedBy(installment, ownerId);

        when(
                pageRepository.findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                )
        ).thenReturn(
                List.of(first, second, third)
        );

        publicationService.reorderPages(
                ownerId,
                installment.getId(),
                new ReorderPagesCommand(
                        List.of(
                                third.getId(),
                                first.getId(),
                                second.getId()
                        )
                )
        );

        assertThat(third.getNumber()).isEqualTo(1);
        assertThat(first.getNumber()).isEqualTo(2);
        assertThat(second.getNumber()).isEqualTo(3);

        verify(installmentRepository)
                .findByIdForUpdate(
                        installment.getId()
                );

        verify(
                installmentRepository,
                never()
        ).findById(
                installment.getId()
        );

        verify(pageRepository).flush();

        verifyNoInteractions(binaryStorage);
    }

    @Test
    void shouldRejectIncompletePageOrder() {
        UUID ownerId = UUID.randomUUID();
        Installment installment = draftInstallment();

        Page first = page(installment, 1);
        Page second = page(installment, 2);

        givenInstallmentOwnedBy(installment, ownerId);

        when(
                pageRepository.findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                )
        ).thenReturn(
                List.of(first, second)
        );

        assertThatThrownBy(() ->
                publicationService.reorderPages(
                        ownerId,
                        installment.getId(),
                        new ReorderPagesCommand(
                                List.of(first.getId())
                        )
                )
        ).isInstanceOf(
                InvalidPageOrderException.class
        );

        assertThat(first.getNumber()).isEqualTo(1);
        assertThat(second.getNumber()).isEqualTo(2);

        verify(pageRepository, never()).flush();
    }

    @Test
    void shouldRejectDuplicatePageIds() {
        UUID ownerId = UUID.randomUUID();
        Installment installment = draftInstallment();

        Page first = page(installment, 1);
        Page second = page(installment, 2);

        givenInstallmentOwnedBy(installment, ownerId);

        when(
                pageRepository.findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                )
        ).thenReturn(
                List.of(first, second)
        );

        assertThatThrownBy(() ->
                publicationService.reorderPages(
                        ownerId,
                        installment.getId(),
                        new ReorderPagesCommand(
                                List.of(
                                        first.getId(),
                                        first.getId()
                                )
                        )
                )
        ).isInstanceOf(
                InvalidPageOrderException.class
        );

        assertThat(first.getNumber()).isEqualTo(1);
        assertThat(second.getNumber()).isEqualTo(2);

        verify(pageRepository, never()).flush();
    }

    @Test
    void shouldRejectPageFromAnotherInstallment() {
        UUID ownerId = UUID.randomUUID();
        Installment installment = draftInstallment();

        Page first = page(installment, 1);
        Page second = page(installment, 2);

        Page foreignPage =
                new Page(
                        UUID.randomUUID(),
                        1,
                        "installments/other/page",
                        "image/jpeg"
                );

        givenInstallmentOwnedBy(installment, ownerId);

        when(
                pageRepository.findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                )
        ).thenReturn(
                List.of(first, second)
        );

        assertThatThrownBy(() ->
                publicationService.reorderPages(
                        ownerId,
                        installment.getId(),
                        new ReorderPagesCommand(
                                List.of(
                                        first.getId(),
                                        foreignPage.getId()
                                )
                        )
                )
        ).isInstanceOf(
                InvalidPageOrderException.class
        );

        assertThat(first.getNumber()).isEqualTo(1);
        assertThat(second.getNumber()).isEqualTo(2);

        verify(pageRepository, never()).flush();
    }

    @Test
    void shouldRejectReorderingByNonOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();

        Installment installment = draftInstallment();

        givenInstallmentOwnedBy(installment, ownerId);

        assertThatThrownBy(() ->
                publicationService.reorderPages(
                        anotherUserId,
                        installment.getId(),
                        new ReorderPagesCommand(
                                List.of(UUID.randomUUID())
                        )
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(installmentRepository)
                .findByIdForUpdate(
                        installment.getId()
                );

        verifyNoInteractions(pageRepository);
    }

    @Test
    void shouldRejectReorderingPublishedInstallment() {
        UUID ownerId = UUID.randomUUID();

        Installment installment = draftInstallment();
        installment.publish();

        when(
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        assertThatThrownBy(() ->
                publicationService.reorderPages(
                        ownerId,
                        installment.getId(),
                        new ReorderPagesCommand(
                                List.of(UUID.randomUUID())
                        )
                )
        ).isInstanceOf(
                PublishedInstallmentModificationException.class
        );

        verify(installmentRepository)
                .findByIdForUpdate(
                        installment.getId()
                );

        verifyNoInteractions(catalogService);
        verifyNoInteractions(pageRepository);
    }

    private Installment draftInstallment() {
        return new Installment(
                UUID.randomUUID(),
                InstallmentType.CHAPTER,
                1,
                null
        );
    }

    private Page page(
            Installment installment,
            int number
    ) {
        return new Page(
                installment.getId(),
                number,
                "installments/example/page-" + number,
                "image/jpeg"
        );
    }

    private void givenInstallmentOwnedBy(
            Installment installment,
            UUID ownerId
    ) {
        when(
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(
                Optional.of(installment)
        );

        when(
                catalogService.findWork(
                        installment.getWorkId()
                )
        ).thenReturn(
                new WorkReference(
                        installment.getWorkId(),
                        ownerId
                )
        );
    }
}