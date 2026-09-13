package com.behcm.global.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 비동기 작업({@code @Async})에 제출 시점의 MDC 를 옮겨 준다.
 *
 * <p>MDC 는 스레드 로컬이라 워커 스레드에서는 비어 있다. 이 데코레이터가 없으면 FCM 발송·메일 발송
 * 로그에 requestId/memberId 가 없어 "어느 요청이 이 푸시를 만들었나"를 잇지 못한다.
 * 작업이 끝나면 워커 스레드의 MDC 를 비워 풀 재사용 시 다음 작업에 새지 않게 한다.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
