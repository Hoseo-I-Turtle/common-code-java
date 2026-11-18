package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OrderKeyUtilsTest {

    @Test
    void initialAfterBefore() {
        String first = OrderKeyUtils.initial();
        assertEquals("i", first);

        String last = OrderKeyUtils.after(first);
        String top = OrderKeyUtils.before(first);
        assertTrue(top.compareTo(first) < 0);
        assertTrue(first.compareTo(last) < 0);
        assertTrue(OrderKeyUtils.isValid(top));
        assertTrue(OrderKeyUtils.isValid(last));
    }

    @Test
    void betweenKnownCases() {
        assertEquals("ai", OrderKeyUtils.between("a", "b"));
        assertEquals("a0i", OrderKeyUtils.between("a", "a1"));
        assertEquals("b", OrderKeyUtils.between("a", "b5"));
        assertEquals("zi", OrderKeyUtils.between("z", null));
        assertEquals("09", OrderKeyUtils.between(null, "0i"));
        assertEquals("0i", OrderKeyUtils.between(null, "1"));
    }

    @Test
    void repeatedInsertKeepsOrderAndValidity() {
        // 같은 자리(맨 앞, 두 키 사이, 맨 뒤)에 계속 끼워 넣어도 순서가 유지된다
        String low = OrderKeyUtils.initial();
        String high = OrderKeyUtils.after(low);
        for (int i = 0; i < 200; i++) {
            String mid = OrderKeyUtils.between(low, high);
            assertTrue(low.compareTo(mid) < 0 && mid.compareTo(high) < 0, low + " < " + mid + " < " + high);
            assertTrue(OrderKeyUtils.isValid(mid), mid);
            high = mid;
        }
        String front = OrderKeyUtils.initial();
        for (int i = 0; i < 200; i++) {
            String next = OrderKeyUtils.before(front);
            assertTrue(next.compareTo(front) < 0);
            assertTrue(OrderKeyUtils.isValid(next), next);
            front = next;
        }
    }

    @Test
    void randomMovesMatchListOrder() {
        // 무작위 위치에 넣으면서 문자열 정렬이 목록 순서와 같은지 확인한다
        Random random = new Random(42);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            int index = random.nextInt(list.size() + 1);
            String before = index == 0 ? null : list.get(index - 1);
            String after = index == list.size() ? null : list.get(index);
            String key = OrderKeyUtils.between(before, after);
            assertTrue(OrderKeyUtils.isValid(key), key);
            list.add(index, key);
        }
        List<String> sorted = new ArrayList<>(list);
        sorted.sort(null);
        assertEquals(sorted, list);
        // 대소문자 무시 정렬에서도 같은 순서 (소문자만 쓰므로)
        List<String> ignoreCase = new ArrayList<>(list);
        ignoreCase.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals(list, ignoreCase);
    }

    @Test
    void evenly() {
        assertEquals(List.of(), OrderKeyUtils.evenly(0));
        assertEquals(List.of("9", "i", "r"), OrderKeyUtils.evenly(3));

        List<String> keys = OrderKeyUtils.evenly(500);
        assertEquals(500, keys.size());
        for (int i = 0; i < keys.size(); i++) {
            assertTrue(OrderKeyUtils.isValid(keys.get(i)), keys.get(i));
            if (i > 0) {
                assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0);
            }
        }
        // 다시 매긴 키 사이에도 넣을 수 있다
        assertTrue(OrderKeyUtils.isValid(OrderKeyUtils.between(keys.get(0), keys.get(1))));
        assertThrows(IllegalArgumentException.class, () -> OrderKeyUtils.evenly(-1));
    }

    @Test
    void rejectsInvalidInput() {
        assertFalse(OrderKeyUtils.isValid(null));
        assertFalse(OrderKeyUtils.isValid(""));
        assertFalse(OrderKeyUtils.isValid("a0"));    // 0 으로 끝남
        assertFalse(OrderKeyUtils.isValid("aB"));    // 대문자
        assertFalse(OrderKeyUtils.isValid("a-b"));
        assertThrows(IllegalArgumentException.class, () -> OrderKeyUtils.between("b", "a"));
        assertThrows(IllegalArgumentException.class, () -> OrderKeyUtils.between("a", "a"));
        assertThrows(IllegalArgumentException.class, () -> OrderKeyUtils.after("A"));
        assertThrows(IllegalArgumentException.class, () -> OrderKeyUtils.before(null));
    }
}
