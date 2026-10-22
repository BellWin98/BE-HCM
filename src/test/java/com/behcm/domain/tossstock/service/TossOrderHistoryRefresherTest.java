package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestProperties;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 매일 밤 주문내역 스냅샷 재적재.
 *
 * <p>스케줄러에서 예외가 새면 스택트레이스만 남고 다른 소유자는 건너뛴다 — 한 사람의 실패가 가족 전체의
 * 재적재를 막지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossOrderHistoryRefresherTest {

    @Mock
    private TossInvestProperties properties;
    @Mock
    private TossAccountResolver accountResolver;
    @Mock
    private TossOrderHistoryReader orderHistoryReader;

    private TossOrderHistoryRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new TossOrderHistoryRefresher(properties, accountResolver, orderHistoryReader);
        given(accountResolver.resolveAccountSeq(TossAccountOwner.ME)).willReturn(1L);
        given(accountResolver.resolveAccountSeq(TossAccountOwner.MOM)).willReturn(2L);
    }

    @Test
    @DisplayName("연동된 소유자 전원의 스냅샷을 다시 적재한다")
    void rebuildsEveryConfiguredOwner() {
        given(properties.configuredOwners()).willReturn(List.of(TossAccountOwner.ME, TossAccountOwner.MOM));

        refresher.refreshNightly();

        verify(orderHistoryReader).rebuild(TossAccountOwner.ME, 1L);
        verify(orderHistoryReader).rebuild(TossAccountOwner.MOM, 2L);
    }

    @Test
    @DisplayName("한 소유자가 실패해도 다음 소유자는 다시 적재하고, 예외를 밖으로 내보내지 않는다")
    void continuesAfterOneOwnerFails() {
        given(properties.configuredOwners()).willReturn(List.of(TossAccountOwner.ME, TossAccountOwner.MOM));
        willThrow(new CustomException(ErrorCode.TOSS_API_FAILED))
                .given(orderHistoryReader).rebuild(TossAccountOwner.ME, 1L);

        assertThatCode(() -> refresher.refreshNightly()).doesNotThrowAnyException();

        verify(orderHistoryReader).rebuild(TossAccountOwner.MOM, 2L);
    }

    @Test
    @DisplayName("계좌를 못 찾은 소유자는 건너뛴다")
    void skipsOwnerWhoseAccountCannotBeResolved() {
        given(properties.configuredOwners()).willReturn(List.of(TossAccountOwner.ME, TossAccountOwner.MOM));
        given(accountResolver.resolveAccountSeq(TossAccountOwner.ME))
                .willThrow(new CustomException(ErrorCode.TOSS_ACCOUNT_NOT_FOUND));

        refresher.refreshNightly();

        verify(orderHistoryReader, never()).rebuild(TossAccountOwner.ME, 1L);
        verify(orderHistoryReader).rebuild(TossAccountOwner.MOM, 2L);
    }

    @Test
    @DisplayName("연동된 계좌가 없으면 아무것도 하지 않는다")
    void doesNothingWithoutOwners() {
        given(properties.configuredOwners()).willReturn(List.of());

        refresher.refreshNightly();

        verify(orderHistoryReader, never()).rebuild(any(), any());
    }
}
