package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_EVENTS;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.RestController;

import com.docsportal.docs.live.SseHub;
import com.docsportal.docs.live.TooManyConnectionsException;

/**
 * 문서·스펙 변경 실시간 알림(SSE). EventSource 는 Authorization 헤더를 못 보내므로 /docs/** 처럼 인증이 없고,
 * 이벤트에는 본문이 없다.
 */
@RestController
public class LiveEventsController {

    private final SseHub hub;

    public LiveEventsController(SseHub hub) {
        this.hub = hub;
    }

    @GetMapping(value = API_EVENTS, produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter events() {
        try {
            return hub.subscribe();
        } catch (TooManyConnectionsException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }
}
