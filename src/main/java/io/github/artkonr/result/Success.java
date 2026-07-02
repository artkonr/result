package io.github.artkonr.result;

/**
 * A sealed record representing a successful Done with no value.
 * <pre>{@code
 * Done<IOException> success = new Success<>();
 * success.isSuccess(); // true
 * }</pre>
 * @param <E> exception type for type-safety
 */
public record Success<E extends Exception>() implements Done<E> {

}
