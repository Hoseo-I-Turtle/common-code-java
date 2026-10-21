package com.iturtle.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 개인정보 마스킹 유틸. null / blank 입력은 그대로 반환한다.
 */
public final class MaskingUtils {

    private static final char MASK = '*';

    private static final Pattern PHONE = Pattern.compile("^(\\d{2,3})(-?)(\\d{3,4})(-?)(\\d{4})$");
    private static final Pattern RRN = Pattern.compile("^(\\d{6})(-?)(\\d)(\\d{6})$");

    private MaskingUtils() {}

    /**
     * 이름: 첫 글자와 마지막 글자만 노출. (홍길동 → 홍*동, 홍길 → 홍*, 남궁민수 → 남**수)
     */
    public static String name(String value) {
        if (isBlank(value)) {
            return value;
        }
        int length = value.length();
        if (length == 1) {
            return value;
        }
        if (length == 2) {
            return value.charAt(0) + String.valueOf(MASK);
        }
        return mask(value, 1, length - 1);
    }

    /**
     * 전화번호: 가운데 자리 마스킹. (010-1234-5678 → 010-****-5678)
     * 형식이 맞지 않으면 마지막 4자리를 제외한 숫자를 마스킹한다.
     */
    public static String phone(String value) {
        if (isBlank(value)) {
            return value;
        }
        Matcher m = PHONE.matcher(value);
        if (m.matches()) {
            return m.group(1) + m.group(2) + repeat(m.group(3).length()) + m.group(4) + m.group(5);
        }
        return maskDigitsExceptLast(value, 4);
    }

    /**
     * 이메일: 로컬 파트 앞 2자리만 노출. (abcdef@gmail.com → ab****@gmail.com, ab@gmail.com → a*@gmail.com)
     */
    public static String email(String value) {
        if (isBlank(value)) {
            return value;
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return mask(value, 0, value.length());
        }
        int visible = at > 2 ? 2 : 1;
        return mask(value, visible, at);
    }

    /**
     * 주민등록번호: 성별 자리 이후 마스킹. (900101-1234567 → 900101-1******)
     */
    public static String rrn(String value) {
        if (isBlank(value)) {
            return value;
        }
        Matcher m = RRN.matcher(value);
        if (m.matches()) {
            return m.group(1) + m.group(2) + m.group(3) + repeat(m.group(4).length());
        }
        return mask(value, 0, value.length());
    }

    /**
     * 카드번호: 앞 4자리, 뒤 4자리만 노출. 구분자는 유지. (1234-5678-9012-3456 → 1234-****-****-3456)
     */
    public static String cardNumber(String value) {
        if (isBlank(value)) {
            return value;
        }
        int totalDigits = countDigits(value);
        StringBuilder sb = new StringBuilder(value.length());
        int digitIndex = 0;
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(digitIndex >= 4 && digitIndex < totalDigits - 4 ? MASK : c);
                digitIndex++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 계좌번호: 마지막 4자리를 제외한 숫자 마스킹. 구분자는 유지. (110-123-456789 → ***-***-**6789)
     */
    public static String accountNumber(String value) {
        if (isBlank(value)) {
            return value;
        }
        return maskDigitsExceptLast(value, 4);
    }

    /**
     * 범용: [start, end) 구간을 마스킹. 범위는 문자열 길이에 맞게 보정된다.
     */
    public static String mask(String value, int start, int end) {
        if (isBlank(value)) {
            return value;
        }
        int from = Math.max(0, start);
        int to = Math.min(value.length(), end);
        if (from >= to) {
            return value;
        }
        return value.substring(0, from) + repeat(to - from) + value.substring(to);
    }

    private static String maskDigitsExceptLast(String value, int visible) {
        int maskCount = countDigits(value) - visible;
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c) && maskCount > 0) {
                sb.append(MASK);
                maskCount--;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int countDigits(String value) {
        return (int) value.chars().filter(Character::isDigit).count();
    }

    private static String repeat(int count) {
        return String.valueOf(MASK).repeat(count);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
