package io.github.artkonr.result;

import java.util.Optional;

/**
 * A rule of how to combine results.
 */
public enum TakeFrom {

    /**
     * Instructs to take the first instance: either
     * the first item in the collection or the the
     * left item in the fuse.
     */
    HEAD,

    /**
     * Instructs to take the first instance: either
     * the first item in the collection or the the
     * right item in the fuse.
     */
    TAIL;

    /**
     * Use the rule to derive which error is returned.
     * @param left head
     * @param right tail
     * @return picked error or empty optional
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
