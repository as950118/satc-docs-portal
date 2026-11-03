package com.docsportal.docs.live;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

/**
 * 연결된 SSE 클라이언트 목록과 전송. 인스턴스 하나만 가정하며 이벤트를 보관하지 않는다.
 * 전송은 전용 단일 스레드(liveEventExecutor)에서 하고, 한 emitter 에는 동시에 쓰지 않는다.
 */
@Component
public class SseHub {

    public static final long DEFAULT_TIMEOUT_MILLIS = 30L * 60 * 1000;
    public static final int DEFAULT_MAX_CONNECTIONS = 200;
    private static final MediaType UTF8_TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final Logger logger = LoggerFactory.getLogger(SseHub.class);

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final int maxConnections;
    private final long timeoutMillis;
    private final Executor liveEventExecutor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public SseHub(@Value("${docs.live.max-connections:" + DEFAULT_MAX_CONNECTIONS + "}") int maxConnections) {
        this(maxConnections, DEFAULT_TIMEOUT_MILLIS, null);
    }

    public SseHub(int maxConnections, long timeoutMillis, Executor executor) {
        this.maxConnections = maxConnections;
        this.timeoutMillis = timeoutMillis;
        if (executor == null) {
            this.ownedExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "liveEventExecutor");
                thread.setDaemon(true);
                return thread;
            });
            this.liveEventExecutor = ownedExecutor;
        } else {
            this.ownedExecutor = null;
            this.liveEventExecutor = executor;
        }
    }

    /**
     * 새 연결을 등록하고 hello 를 보낸다.
     *
     * @throws TooManyConnectionsException 연결 상한을 넘을 때
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = newEmitter(timeoutMillis);
        synchronized (this) {
            if (emitters.size() >= maxConnections) {
                throw new TooManyConnectionsException(maxConnections);
            }
            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onTimeout(() -> {
                emitters.remove(emitter);
                emitter.complete();
            });
            emitter.onError(e -> emitters.remove(emitter));
            // 등록과 hello 큐잉을 같은 락에서 하고 broadcast 의 큐잉도 같은 락을 쓰므로, hello 가 항상 첫 메시지다
            emitters.add(emitter);
            submit(() -> send(emitter, data(SseEmitter.event().name("hello"), LiveEvent.helloJson(Instant.now()))));
        }
        return emitter;
    }

    /** 테스트가 전송 실패 등을 흉내 내려고 바꿔 끼울 수 있다. */
    protected SseEmitter newEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    public void broadcast(LiveEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        submit(() -> {
            SseEmitter.SseEventBuilder message = data(SseEmitter.event().name(event.eventName()), event.toJson());
            for (SseEmitter emitter : emitters) {
                send(emitter, message);
            }
        });
    }

    /** 프록시·로드밸런서가 유휴 연결을 끊지 않도록 주기적으로 주석 줄을 보낸다. */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        submit(() -> {
            for (SseEmitter emitter : emitters) {
                send(emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    public int connectionCount() {
        return emitters.size();
    }

    @PreDestroy
    void shutdown() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }

    /** 제목에 한글이 들어가므로 data 를 명시적으로 UTF-8 로 쓴다(기본 변환기는 ISO-8859-1 로 쓸 수 있다). */
    private static SseEmitter.SseEventBuilder data(SseEmitter.SseEventBuilder builder, String json) {
        return builder.data(json, UTF8_TEXT);
    }

    private synchronized void submit(Runnable task) {
        try {
            liveEventExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            logger.debug("[LIVE] executor closed, dropping task");
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder message) {
        synchronized (emitter) {
            try {
                emitter.send(message);
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // 이미 끊긴 연결
                }
            }
        }
    }
}
