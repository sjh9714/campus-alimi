package com.campusalimi.notice;

/** 공지 그림 한 장의 글자를 그대로 옮겨 적는다. 요약하거나 날짜를 판단하지 않는다 (ADR 010). */
public interface ImageTranscriber {

    record Result(String text, String model, long inputTokens, long outputTokens) {
    }

    boolean enabled();

    /** 실패하면 예외를 던진다. 실패한 그림은 기록하지 않으므로 다음 차례에 다시 시도한다. */
    Result transcribe(byte[] image, String mediaType);
}
