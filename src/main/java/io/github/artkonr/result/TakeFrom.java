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
     * @param head head
     * @param tail tail
     * @return picked error or empty optional
     * @param <E> error type
     */
    <E extends Exception> Optional<E> takeError(BaseResult<E> head, BaseResult<E> tail) {
        if (head.isErr() && tail.isErr()) {
            E picked = switch (this) {
                case HEAD -> head.error;
                case TAIL -> tail.error;
            };
            return Optional.of(picked);
        }

        if (head.isErr()) {
            return Optional.of(head.error);
        }

        if (tail.isErr()) {
            return Optional.of(tail.error);
        }

        return Optional.empty();
    }

}
