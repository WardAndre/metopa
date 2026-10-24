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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentDeletionServiceTests {

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
    void shouldDeleteDraftInstallmentAndPageFilesForOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                "Chapter One"
        );

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
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(workId, ownerId)
                );

        when(
                pageRepository.findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                )
        ).thenReturn(
                List.of(firstPage, secondPage)
        );


        TransactionSynchronizationManager.initSynchronization();

        try {
            publicationService.deleteInstallment(
                    ownerId,
                    installment.getId()
            );

            // Files must not be deleted before commit.
            verifyNoInteractions(binaryStorage);

            var synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertThat(synchronizations).hasSize(1);

            // Simulate a successful transaction commit.
            synchronizations.forEach(
                    TransactionSynchronization::afterCommit
            );

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }


        InOrder deletionOrder = inOrder(
                installmentRepository,
                binaryStorage
        );

        deletionOrder.verify(installmentRepository)
                .findByIdForUpdate(installment.getId());

        deletionOrder.verify(installmentRepository)
                .delete(installment);

        deletionOrder.verify(installmentRepository)
                .flush();

        deletionOrder.verify(binaryStorage)
                .delete(firstPage.getStorageKey());

        deletionOrder.verify(binaryStorage)
                .delete(secondPage.getStorageKey());
    }


    @Test
    void shouldRejectDeletionOfPublishedInstallment() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                "Published Chapter"
        );

        installment.publish();

        when(
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(workId, userId)
                );

        assertThatThrownBy(() ->
                publicationService.deleteInstallment(
                        userId,
                        installment.getId()
                )
        ).isInstanceOf(
                PublishedInstallmentModificationException.class
        );

        verify(installmentRepository, never())
                .delete(any(Installment.class));

        verifyNoInteractions(
                pageRepository,
                binaryStorage
        );
    }


    @Test
    void shouldRejectDeletionOfUnknownInstallment() {
        UUID userId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        when(
                installmentRepository.findByIdForUpdate(
                        installmentId
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                publicationService.deleteInstallment(
                        userId,
                        installmentId
                )
        ).isInstanceOf(
                InstallmentNotFoundException.class
        );

        verify(installmentRepository, never())
                .delete(any(Installment.class));

        verifyNoInteractions(
                catalogService,
                pageRepository,
                binaryStorage
        );
    }

    @Test
    void shouldRejectDeletionByNonOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                "Chapter One"
        );

        when(
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(workId, ownerId)
                );

        assertThatThrownBy(() ->
                publicationService.deleteInstallment(
                        anotherUserId,
                        installment.getId()
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(installmentRepository, never())
                .delete(any(Installment.class));

        verifyNoInteractions(
                pageRepository,
                binaryStorage
        );
    }

    @Test
    void shouldDeleteDraftInstallmentWithoutPages() {
        UUID ownerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                "Empty Chapter"
        );

        when(
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(
                        new WorkReference(workId, ownerId)
                );

        when(
                pageRepository.findByInstallmentIdOrderByNumberAsc(
                        installment.getId()
                )
        ).thenReturn(List.of());

        publicationService.deleteInstallment(
                ownerId,
                installment.getId()
        );

        verify(installmentRepository)
                .findByIdForUpdate(installment.getId());

        verify(installmentRepository)
                .delete(installment);

        verify(installmentRepository)
                .flush();

        verifyNoInteractions(binaryStorage);
    }


    @Test
    void shouldRejectPublishedInstallmentDeletionByNonOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                "Published Chapter"
        );

        installment.publish();

        when(
                installmentRepository.findByIdForUpdate(
                        installment.getId()
                )
        ).thenReturn(Optional.of(installment));

        when(catalogService.findWork(workId))
                .thenReturn(new WorkReference(workId, ownerId));

        assertThatThrownBy(() ->
                publicationService.deleteInstallment(
                        anotherUserId,
                        installment.getId()
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(installmentRepository, never())
                .delete(any(Installment.class));

        verifyNoInteractions(
                pageRepository,
                binaryStorage
        );
    }

}