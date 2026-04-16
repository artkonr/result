package io.github.artkonr.result;

/**
 * A {@link RuntimeException} that wraps a checked exception for throwing without declaration.
 * <p>Used by {@link Result#unwrap()} to convert checked exceptions to unchecked ones.
 * <pre>{@code
 * Result<String, IOException> result = ...;
 * try {
 *   String data = result.unwrap(); // throws Failure if ERR
 * } catch (Failure e) {
 *   Throwable cause = e.getCause(); // get the original exception
 * }
 * }</pre>
 */
public class Failure extends RuntimeException {

    /**
     * Creates a Failure wrapping a checked exception.
     * <pre>{@code
     * throw new Failure(new IOException("read failed"));
     * }</pre>
     * @param cause the wrapped exception
     */
    public Failure(Throwable cause) {
        super(cause);
    }
}
