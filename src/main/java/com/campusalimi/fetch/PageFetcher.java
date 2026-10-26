package com.campusalimi.fetch;

public interface PageFetcher {

    /** 페이지를 읽어 HTML을 돌려준다. 실패하면 예외를 던진다. */
    String get(String url);
}
