package com.docsportal.docs.live;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 서비스가 변경을 알리는 내부 API. 스프링 이벤트로 발행하고, 트랜잭션이 있으면 커밋 뒤에(롤백되면 버림),
 * 없으면 즉시 SseHub 로 넘긴다.
 */
@Component
public class LiveEventPublisher {

    private final ApplicationEventPublisher events;
    private final SseHub hub;

    public LiveEventPublisher(ApplicationEventPublisher events, SseHub hub) {
        this.events = events;
        this.hub = hub;
    }

    /** originId 는 현재 요청의 X-Client-Id 에서 읽는다. */
    public void publish(LiveEventType type, String id, String title, String parentId, String status, String actor) {
        publish(type, id, title, parentId, status, actor, OriginIdHolder.current());
    }

    /** 요청 스레드를 벗어난 경로(@Async)는 컨트롤러가 읽어 넘긴 originId 를 쓴다. */
    public void publish(LiveEventType type, String id, String title, String parentId, String status, String actor,
                        String originId) {
        events.publishEvent(new LiveEvent(type, id, title, parentId, status, actor, originId, Instant.now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onLiveEvent(LiveEvent event) {
        hub.broadcast(event);
    }
}
