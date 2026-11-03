package com.docsportal.docs.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseHubTest {

    /** 보낸 메시지를 모으거나, failing 이면 전송 때 IOException 을 던지는 emitter. */
    static class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        final boolean failing;
        boolean closed;

        RecordingEmitter(boolean failing) {
            super(60_000L);
            this.failing = failing;
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            if (closed) {
                throw new IllegalStateException("already completed");
            }
            if (failing) {
                throw new IOException("broken pipe");
            }
            StringBuilder text = new StringBuilder();
            builder.build().forEach(part -> text.append(part.getData()));
            sent.add(text.toString());
        }

        @Override
        public synchronized void complete() {
            closed = true;
        }

        @Override
        public synchronized void send(Object object, MediaType mediaType) throws IOException {
            send(event().data(object));
        }
    }

    private static LiveEvent event() {
        return new LiveEvent(LiveEventType.DOCUMENT_CREATED, "d1", "t", null, "COMPLETE", null, null, Instant.now());
    }

    /** subscribe() 가 만드는 emitter 를 미리 준비한 것으로 바꾸는 허브. 전송은 호출 스레드에서 바로 한다. */
    private static class TestHub extends SseHub {
        final List<RecordingEmitter> next = new ArrayList<>();

        TestHub(int max) {
            super(max, 60_000L, Runnable::run);
        }

        RecordingEmitter prepare(boolean failing) {
            RecordingEmitter emitter = new RecordingEmitter(failing);
            next.add(emitter);
            return emitter;
        }

        @Override
        protected SseEmitter newEmitter(long timeoutMillis) {
            return next.remove(0);
        }
    }

    @Test
    void helloThenBroadcastReachEverySubscriber() {
        TestHub hub = new TestHub(10);
        RecordingEmitter a = hub.prepare(false);
        RecordingEmitter b = hub.prepare(false);
        hub.subscribe();
        hub.subscribe();

        hub.broadcast(event());

        for (RecordingEmitter emitter : List.of(a, b)) {
            assertThat(emitter.sent).hasSize(2);
            assertThat(emitter.sent.get(0)).contains("event:hello").contains("\"type\":\"hello\"");
            assertThat(emitter.sent.get(1)).contains("event:document.created").contains("\"id\":\"d1\"");
        }
    }

    @Test
    void failingEmitterIsRemovedAndOthersStillReceive() {
        TestHub hub = new TestHub(10);
        RecordingEmitter broken = hub.prepare(true);
        RecordingEmitter healthy = hub.prepare(false);
        hub.subscribe();
        hub.subscribe();
        // broken 은 hello 전송에서 이미 실패해 제거된다
        assertThat(hub.connectionCount()).isEqualTo(1);

        hub.broadcast(event());

        assertThat(healthy.sent).hasSize(2);
        assertThat(broken.sent).isEmpty();
    }

    @Test
    void emitterThatFailsLaterIsRemoved() {
        TestHub hub = new TestHub(10);
        RecordingEmitter emitter = hub.prepare(false);
        hub.subscribe();
        assertThat(hub.connectionCount()).isEqualTo(1);

        emitter.complete();
        hub.broadcast(event()); // 완료된 emitter 에 보내면 IllegalStateException -> 제거

        assertThat(hub.connectionCount()).isZero();
    }

    @Test
    void heartbeatSendsPingComment() {
        TestHub hub = new TestHub(10);
        RecordingEmitter emitter = hub.prepare(false);
        hub.subscribe();

        hub.heartbeat();

        assertThat(emitter.sent).hasSize(2);
        assertThat(emitter.sent.get(1)).contains(":ping");
    }

    @Test
    void rejectsConnectionsOverTheLimitAndFreesSlotOnRemoval() {
        TestHub hub = new TestHub(2);
        hub.prepare(false);
        hub.prepare(false);
        hub.prepare(true);
        hub.subscribe();
        hub.subscribe();

        assertThatThrownBy(hub::subscribe).isInstanceOf(TooManyConnectionsException.class);
        assertThat(hub.connectionCount()).isEqualTo(2);
    }
}
