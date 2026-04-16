package io.github.artkonr.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FuseTest {

    @Test
    void should_create_with_left_and_right() {
        Fuse<String, Integer> fuse = new Fuse<>("left", 42);
        assertEquals("left", fuse.left());
        assertEquals(42, fuse.right());
    }

    @Test
    void should_throw_when_left_is_null() {
        assertThrows(IllegalArgumentException.class, () -> new Fuse<>(null, 42));
    }

    @Test
    void should_throw_when_right_is_null() {
        assertThrows(IllegalArgumentException.class, () -> new Fuse<>("left", null));
    }

    @Test
    void should_throw_when_both_null() {
        assertThrows(IllegalArgumentException.class, () -> new Fuse<>(null, null));
    }

    @ParameterizedTest
    @MethodSource("fuseProvider")
    void should_create_with_various_types(Object left, Object right) {
        Fuse<Object, Object> fuse = new Fuse<>(left, right);
        assertSame(left, fuse.left());
        assertSame(right, fuse.right());
    }

    static Stream<Object[]> fuseProvider() {
        return Stream.of(
            new Object[]{"string", 42},
            new Object[]{123, "value"},
            new Object[]{3.14, true},
            new Object[]{new Object(), new Object()}
        );
    }

    @Test
    void should_have_left_accessor() {
        String left = "left value";
        Fuse<String, Integer> fuse = new Fuse<>(left, 42);
        assertSame(left, fuse.left());
    }

    @Test
    void should_have_right_accessor() {
        Integer right = 42;
        Fuse<String, Integer> fuse = new Fuse<>("left", right);
        assertSame(right, fuse.right());
    }

    @Test
    void should_be_record() {
        Fuse<String, Integer> fuse = new Fuse<>("left", 42);
        assertNotNull(fuse.toString());
        assertTrue(fuse.toString().contains("left"));
        assertTrue(fuse.toString().contains("42"));
    }

    @Test
    void should_implement_equals() {
        Fuse<String, Integer> fuse1 = new Fuse<>("left", 42);
        Fuse<String, Integer> fuse2 = new Fuse<>("left", 42);
        Fuse<String, Integer> fuse3 = new Fuse<>("other", 42);

        assertEquals(fuse1, fuse2);
        assertNotEquals(fuse1, fuse3);
    }

    @Test
    void should_implement_hash_code() {
        Fuse<String, Integer> fuse1 = new Fuse<>("left", 42);
        Fuse<String, Integer> fuse2 = new Fuse<>("left", 42);
        assertEquals(fuse1.hashCode(), fuse2.hashCode());
    }

    @Test
    void should_work_in_collections() {
        Fuse<String, Integer> fuse1 = new Fuse<>("left1", 42);
        Fuse<String, Integer> fuse2 = new Fuse<>("left2", 42);

        var set = java.util.Set.of(fuse1, fuse2);
        assertEquals(2, set.size());
    }

    @Test
    void should_distinguish_left_and_right() {
        Fuse<String, String> fuse1 = new Fuse<>("a", "b");
        Fuse<String, String> fuse2 = new Fuse<>("b", "a");
        assertNotEquals(fuse1, fuse2);
    }

}
