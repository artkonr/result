package io.github.artkonr.result;

import lombok.NonNull;

/**
 * Represents a successful result containing a value.
 * <p>An Ok result indicates that an operation completed successfully.
 * <pre>{@code
 * Result<String, IOException> success = new Ok<>("data");
 * success.isOk(); // true
 * success.value(); // "data"
 *
 * String mapped = success.map(s -> s.toUpperCase()).value(); // "DATA"
 * }</pre>
 * @param item OK object
 * @param <V> type of the value
 * @param <E> type of exception this result could have contained
 */
public record Ok<V, E extends Exception>(@NonNull V item) implements Result<V, E> { }