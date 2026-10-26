package com.campusalimi.feed;

import java.util.regex.Pattern;

/**
 * 날짜 항목의 성격. ACTION은 학생이 그날까지 무언가를 해야 하는 날(신청·제출·납부·변경),
 * INFO는 지켜보기만 하는 날(발표·면접·수업기간·수여식)이다. 마감 목록에는 ACTION만 줄 세운다.
 * 모르면 ACTION으로 둔다. 할 일을 안내로 잘못 넣어 놓치는 쪽이 더 위험하다.
 */
public final class ItemKind {

    public static final String ACTION = "ACTION";
    public static final String INFO = "INFO";

    private static final Pattern INFO_TITLE = Pattern.compile(
            "발표|결과|면접|수업\\s*기간|근로\\s*기간|수여식|전달식|시상식|개시일|입학미사|개강|종강|설명회|오리엔테이션"
                    + "|^(대학|재단|최종)\\s*선발$|공고$|^계절학기\\s*수업|^수업|수업$");

    private ItemKind() {
    }

    public static String of(String itemTitle) {
        return itemTitle != null && INFO_TITLE.matcher(itemTitle).find() ? INFO : ACTION;
    }
}
