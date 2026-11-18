package com.iturtle.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 끌어서 순서를 바꾸는 목록(칸반 카드, 고정 글, 체크리스트)의 문자열 순서 키 (fractional indexing).
 * <p>
 * 두 키 사이에 들어갈 새 키를 만들어, 항목 하나를 옮길 때 <b>그 행 하나만</b> 바꾼다. 정수 순서처럼 뒤 항목을 모두 밀지 않는다.
 * <ul>
 *   <li>키는 숫자와 영문 소문자(base36, {@code 0-9a-z})로만 만들고 {@code 0} 으로 끝나지 않는다.
 *       대소문자를 섞지 않으므로 DB 의 대소문자 무시 정렬(MySQL {@code utf8mb4_0900_ai_ci} 등)에서도 문자열 순서와 같다.</li>
 *   <li>정렬은 키 문자열 오름차순이다 ({@code order by order_key}). 같은 키가 생기지 않게 두 이웃 키로만 만든다.</li>
 *   <li>같은 자리에 계속 끼워 넣으면 키가 한 글자씩 길어진다. 컬럼 길이(예: {@code varchar(64)})에 가까워지면
 *       {@link #evenly(int)} 로 그 목록의 키를 다시 매긴다.</li>
 *   <li>두 사람이 같은 이웃 사이에 동시에 넣으면 같은 키가 나올 수 있다. 목록 단위로 잠그거나, 같은 키면 id 로 2차 정렬한다.</li>
 * </ul>
 * <pre>
 * String first = OrderKeyUtils.initial();                 // "i"
 * String last  = OrderKeyUtils.after(first);              // 맨 뒤에 추가
 * String mid   = OrderKeyUtils.between(first, last);      // 두 카드 사이로 옮김
 * String top   = OrderKeyUtils.between(null, first);      // 맨 앞으로 옮김
 * </pre>
 */
public final class OrderKeyUtils {

    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int BASE = DIGITS.length();

    private OrderKeyUtils() {}

    /**
     * 빈 목록의 첫 키.
     */
    public static String initial() {
        return between(null, null);
    }

    /**
     * key 바로 뒤(맨 뒤에 추가)에 들어갈 키.
     */
    public static String after(String key) {
        return between(requireValid(key, "key"), null);
    }

    /**
     * key 바로 앞(맨 앞에 추가)에 들어갈 키.
     */
    public static String before(String key) {
        return between(null, requireValid(key, "key"));
    }

    /**
     * before 와 after 사이에 들어갈 키. before 가 null 이면 맨 앞, after 가 null 이면 맨 뒤.
     *
     * @throws IllegalArgumentException 키 형식이 틀렸거나 before 가 after 보다 앞이 아니면
     */
    public static String between(String before, String after) {
        if (before != null) {
            requireValid(before, "before");
        }
        if (after != null) {
            requireValid(after, "after");
        }
        if (before != null && after != null && before.compareTo(after) >= 0) {
            throw new IllegalArgumentException("before(" + before + ") 는 after(" + after + ") 보다 앞이어야 한다");
        }
        return midpoint(before == null ? "" : before, after);
    }

    /**
     * 키를 count 개 고르게 다시 매긴다 (목록 초기화, 키가 너무 길어졌을 때). 결과는 오름차순이다.
     */
    public static List<String> evenly(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count 는 0 이상이어야 한다");
        }
        List<String> keys = new ArrayList<>(count);
        if (count == 0) {
            return keys;
        }
        int length = 1;
        long space = BASE;
        while (space <= count) {
            length++;
            space *= BASE;
        }
        for (int i = 1; i <= count; i++) {
            long value = space * i / (count + 1);
            keys.add(stripTrailingZeros(toBase36(value, length)));
        }
        return keys;
    }

    /**
     * 키 형식이 맞는지 (비어 있지 않고, {@code 0-9a-z} 만, {@code 0} 으로 끝나지 않음).
     */
    public static boolean isValid(String key) {
        if (key == null || key.isEmpty() || key.charAt(key.length() - 1) == '0') {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (DIGITS.indexOf(key.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * a &lt; 결과 &lt; b 인 가장 짧은 키. a 는 "" 일 수 있고(맨 앞), b 는 null 일 수 있다(맨 뒤).
     */
    private static String midpoint(String a, String b) {
        // 같은 앞부분은 그대로 두고 나머지에서 고른다 (a 가 짧으면 뒤를 0 으로 본다)
        int n = 0;
        while (b != null && n < b.length() && digitAt(a, n) == DIGITS.indexOf(b.charAt(n))) {
            n++;
        }
        if (n > 0) {
            return b.substring(0, n) + midpoint(a.length() > n ? a.substring(n) : "", b.substring(n));
        }
        int digitA = a.isEmpty() ? 0 : DIGITS.indexOf(a.charAt(0));
        int digitB = b == null ? BASE : DIGITS.indexOf(b.charAt(0));
        if (digitB - digitA > 1) {
            return String.valueOf(DIGITS.charAt((digitA + digitB) / 2));
        }
        // 첫 자리가 붙어 있다
        if (b != null && b.length() > 1) {
            return b.substring(0, 1);
        }
        return DIGITS.charAt(digitA) + midpoint(a.length() > 1 ? a.substring(1) : "", null);
    }

    private static int digitAt(String s, int index) {
        return index < s.length() ? DIGITS.indexOf(s.charAt(index)) : 0;
    }

    private static String toBase36(long value, int length) {
        char[] chars = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            chars[i] = DIGITS.charAt((int) (value % BASE));
            value /= BASE;
        }
        return new String(chars);
    }

    private static String stripTrailingZeros(String key) {
        int end = key.length();
        while (end > 1 && key.charAt(end - 1) == '0') {
            end--;
        }
        return key.substring(0, end);
    }

    private static String requireValid(String key, String name) {
        if (!isValid(key)) {
            throw new IllegalArgumentException(name + " 순서 키 형식이 아니다: " + key);
        }
        return key;
    }
}
