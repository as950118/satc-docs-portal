package com.docsportal.docs.live;

public class TooManyConnectionsException extends RuntimeException {

    public TooManyConnectionsException(int max) {
        super("실시간 연결 상한(" + max + ")을 넘었습니다.");
    }
}
