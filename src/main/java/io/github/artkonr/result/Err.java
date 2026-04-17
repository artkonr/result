package io.github.artkonr.result;


import lombok.NonNull;

/**
 * Represents a failed result containing an error.
 * <p>An Err result indicates that an operation failed with an exception.
 * <pre>{@code
 * Result<String, IOException> failure = new Err<>(new IOException("read failed"));
 * failure.isErr(); // true
 * failure.err(); // IOException
 *
 * String recovered = failure.recover("default").value(); // "default"
 * }</pre>
 * @param item error object
 * @param <V> type of value this result could have contained
 * @param <E> type of the error
 */
public record Err<V, E extends Exception>(@NonNull E item) implements Result<V, E> { }