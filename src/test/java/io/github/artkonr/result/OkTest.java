package io.github.artkonr.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OkTest {

    @Test
    void should_create_with_value() {
        Ok<String, Exception> ok = new Ok<>("value");
        assertEquals("value", ok.item());
    }

    @Test
    void should_throw_when_creating_with_null() {
        assertThrows(IllegalArgumentException.class, () -> new Ok<>(null));
    }

    @ParameterizedTest
    @MethodSource("valueProvider")
    void should_create_with_various_types(Object value) {
        Ok<Object, Exception> ok = new Ok<>(value);
        assertSame(value, ok.item());
    }

    static Stream<Object> valueProvider() {
        return Stream.of(
            "string",
            42,
            3.14,
            true,
            new Object()
        );
    }

    @Test
    void should_be_result_instance() {
        Ok<String, Exception> ok = new Ok<>("value");
        assertInstanceOf(Result.class, ok);
    }

    @Test
    void should_have_item_accessor() {
        String value = "test";
        Ok<String, Exception> ok = new Ok<>(value);
        assertEquals(value, ok.item());
    }

    @Test
    void should_be_record() {
        Ok<String, Exception> ok = new Ok<>("value");
        assertNotNull(ok.toString());
        assertTrue(ok.toString().contains("value"));
    }

    @Test
    void should_implement_equals() {
        Ok<String, Exception> ok1 = new Ok<>("value");
        Ok<String, Exception> ok2 = new Ok<>("value");
        Ok<String, Exception> ok3 = new Ok<>("other");

        assertEquals(ok1, ok2);
        assertNotEquals(ok1, ok3);
    }

    @Test
    void should_implement_hash_code() {
        Ok<String, Exception> ok1 = new Ok<>("value");
        Ok<String, Exception> ok2 = new Ok<>("value");
        assertEquals(ok1.hashCode(), ok2.hashCode());
    }

    @Test
    void should_work_in_collections() {
        Ok<String, Exception> ok1 = new Ok<>("value1");
        Ok<String, Exception> ok2 = new Ok<>("value2");

        var set = java.util.Set.of(ok1, ok2);
        assertEquals(2, set.size());
    }

}
