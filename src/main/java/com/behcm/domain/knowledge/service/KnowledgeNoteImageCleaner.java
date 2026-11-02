package com.behcm.domain.knowledge.service;

import com.behcm.global.config.aws.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * 메모에서 떨어진 이미지 파일을 S3 에서 지운다. DB 가 롤백됐는데 파일만 사라지면 안 되므로
 * 트랜잭션 커밋 이후에 지우고, 삭제 실패는 메모 저장 결과에 영향을 주지 않도록 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNoteImageCleaner {

    private final S3Service s3Service;

    public void deleteAfterCommit(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<String> targets = List.copyOf(urls);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAll(targets);
                }
            });
        } else {
            deleteAll(targets);
        }
    }

    private void deleteAll(List<String> urls) {
        for (String url : urls) {
            // deleteByUrl 은 버킷 안 어떤 키든 지우므로, 메모 이미지 경로가 아닌 URL 은 여기서 막는다.
            if (!s3Service.isKnowledgeNoteImageUrl(url)) {
                log.warn("Knowledge note image delete skipped: not a knowledge note image URL (url={})", url);
                continue;
            }
            try {
                s3Service.deleteByUrl(url);
            } catch (Exception e) {
                log.warn("Knowledge note image delete failed (url={})", url, e);
            }
        }
    }
}
