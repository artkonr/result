package io.github.artkonr.result;

import lombok.NonNull;

/**
 * A sealed record representing a failed Done with an exception.
 * <pre>{@code
 * IOException error = new IOException("read failed");
 * Done<IOException> failure = new Failure<>(error);
 * failure.isFailure(); // true
 * failure.failure(); // IOException
 * }</pre>
 * @param ex the exception indicating failure
 * @param <E> exception type
 */
public record Failure<E extends Exception>(@NonNull E ex) implements Done<E> {

}
