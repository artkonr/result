package io.github.artkonr.result;

import java.util.Optional;

/**
 * Rule for selecting which error to return when combining multiple Results.
 * <pre>{@code
 * Result<Integer, IOException> a = new Err<>(new IOException("err1"));
 * Result<String, IOException> b = new Err<>(new IOException("err2"));
 *
 * Result<Fuse<Integer, String>, IOException> fused1 = a.fuse(b, TakeFrom.HEAD);
 * // Selects error from a ("err1")
 *
 * Result<Fuse<Integer, String>, IOException> fused2 = a.fuse(b, TakeFrom.TAIL);
 * // Selects error from b ("err2")
 * }</pre>
 */
public enum TakeFrom {

    /**
     * Select the error from the first Result (left/head).
     * <p>When combining multiple errors, prefer the error that occurred first.
     */
    HEAD,

    /**
     * Select the error from the second Result (right/tail).
     * <p>When combining multiple errors, prefer the error that occurred second.
     */
    TAIL;

    /**
     * Selects which error to return based on this rule.
     * <pre>{@code
     * Optional<IOException> error = TakeFrom.HEAD.takeError(
     *   new Ok<>(1),
     *   new Err<>(new IOException())
     * );
     * // Returns Optional.of(IOException)
     * }</pre>
     * @param head first result
     * @param tail second result
     * @return selected error, or empty if both are OK
     * @param <V> head value type
     * @param <N> tail value type
     * @param <E> error type
     */
    <V, N, E extends Exception> Optional<E> takeError(Result<V, E> head, Result<N, E> tail) {
      
        if (head.isErr() && tail.isErr()) {
            E picked = switch (this) {
                case HEAD -> head.err();
                case TAIL -> tail.err();
            };
            return Optional.of(picked);
        }

        if (head.isErr()) {
            return Optional.of(head.err());
        }

        if (tail.isErr()) {
            return Optional.of(tail.err());
        }

        return Optional.empty();
    }

}
