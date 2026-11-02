package com.behcm.domain.knowledge.service;

import com.behcm.global.config.aws.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeNoteImageCleanerTest {

    private static final String A = "https://b.s3.amazonaws.com/knowledge-notes/a.png";
    private static final String B = "https://b.s3.amazonaws.com/knowledge-notes/b.png";

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private KnowledgeNoteImageCleaner cleaner;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 전까지 지우지 않고 afterCommit 에 지운다")
    void deletesOnlyAfterCommit() {
        when(s3Service.isKnowledgeNoteImageUrl(anyString())).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(List.of(A, B));

        verify(s3Service, never()).deleteByUrl(anyString());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(s3Service).deleteByUrl(A);
        verify(s3Service).deleteByUrl(B);
    }

    @Test
    @DisplayName("롤백되면 지우지 않는다")
    void rollback_deletesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(List.of(A));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("트랜잭션 동기화가 없으면 즉시 지운다")
    void withoutSynchronization_deletesImmediately() {
        when(s3Service.isKnowledgeNoteImageUrl(anyString())).thenReturn(true);

        cleaner.deleteAfterCommit(List.of(A));

        verify(s3Service).deleteByUrl(A);
    }

    @Test
    @DisplayName("한 URL 삭제가 실패해도 나머지를 계속 지운다")
    void failureOnOne_continuesWithRest() {
        when(s3Service.isKnowledgeNoteImageUrl(anyString())).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(s3Service).deleteByUrl(A);

        cleaner.deleteAfterCommit(List.of(A, B));

        verify(s3Service).deleteByUrl(B);
    }

    @Test
    @DisplayName("knowledge-notes/ 이미지 URL 이 아니면 지우지 않는다")
    void skipsUrlsOutsideKnowledgeNotePrefix() {
        when(s3Service.isKnowledgeNoteImageUrl(A)).thenReturn(false);
        when(s3Service.isKnowledgeNoteImageUrl(B)).thenReturn(true);

        cleaner.deleteAfterCommit(List.of(A, B));

        verify(s3Service, never()).deleteByUrl(A);
        verify(s3Service).deleteByUrl(B);
    }

    @Test
    @DisplayName("지울 URL 이 없으면 동기화도 등록하지 않는다")
    void emptyUrls_doesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(List.of());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(s3Service);
    }
}
