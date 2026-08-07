package kh.edu.istad.ite.devsoleapi.common.storage;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageStorageServiceTest {

    private static final String PREVIOUS_URL =
            "https://cdn.example.com/devsole/public/categories/abc/old.png";

    private final AttachmentValidator attachmentValidator =
            new AttachmentValidator();
    private final ObjectStorageService objectStorageService =
            mock(ObjectStorageService.class);
    private final ImageStorageService imageStorageService =
            new ImageStorageService(attachmentValidator, objectStorageService);

    private final MockMultipartFile png = new MockMultipartFile(
            "file",
            "icon.png",
            "image/png",
            new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A
            }
    );

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storesUnderThePublicPrefixAndReturnsThePublicUrl() {
        when(objectStorageService.publicUrl(anyString()))
                .thenAnswer(invocation ->
                        "https://cdn.example.com/devsole/"
                                + invocation.getArgument(0));

        String url = imageStorageService.replace(
                "categories/abc",
                null,
                png
        );

        verify(objectStorageService).store(
                org.mockito.ArgumentMatchers.startsWith(
                        "public/categories/abc/"
                ),
                any(),
                anyLong(),
                org.mockito.ArgumentMatchers.eq("image/png")
        );
        assertTrue(url.contains("/public/categories/abc/"));
        assertTrue(url.endsWith(".png"));
    }

    @Test
    void deletesThePreviousObjectOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(objectStorageService.storageKeyOf(PREVIOUS_URL))
                .thenReturn(Optional.of("public/categories/abc/old.png"));

        imageStorageService.replace("categories/abc", PREVIOUS_URL, png);

        verify(objectStorageService, never()).delete(anyString());

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(objectStorageService).delete("public/categories/abc/old.png");
    }

    @Test
    void deletesTheNewObjectWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        when(objectStorageService.storageKeyOf(PREVIOUS_URL))
                .thenReturn(Optional.of("public/categories/abc/old.png"));

        imageStorageService.replace("categories/abc", PREVIOUS_URL, png);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(objectStorageService, never())
                .delete("public/categories/abc/old.png");
        verify(objectStorageService).delete(
                org.mockito.ArgumentMatchers.startsWith(
                        "public/categories/abc/"
                )
        );
    }

    @Test
    void neverDeletesAnExternallyHostedImage() {
        TransactionSynchronizationManager.initSynchronization();
        String external = "https://images.unsplash.com/photo-1234.png";
        when(objectStorageService.storageKeyOf(external))
                .thenReturn(Optional.empty());

        imageStorageService.remove(external);
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(objectStorageService, never()).delete(anyString());
    }

    @Test
    void rejectsANonImageBeforeTouchingStorage() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "handbook.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes()
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> imageStorageService.replace("categories/abc", null, pdf)
        );

        verify(objectStorageService, never())
                .store(anyString(), any(), anyLong(), anyString());
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(
                synchronization -> synchronization.afterCompletion(status)
        );
    }
}
