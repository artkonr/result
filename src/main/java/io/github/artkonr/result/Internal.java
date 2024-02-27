package io.github.artkonr.result;

/**
 * Internal holder for the checked exception lambda wrappers.
 */
class Internal {

    /**
     * A wrapping {@link java.lang.Runnable}-like that allows
     *  throwing checked exceptions inside the lambda block.
     * @param <E> error type
     */
    interface Runnable<E extends Exception> {
        /**
         * Performs the specified action.
         * @throws E possibly checked error type
         */
        void run() throws E;
    }

    /**
     * A wrapping {@link java.util.function.Supplier}-like that allows
     *  throwing checked exceptions inside the lambda block.
     * @param <V> value type
     * @param <E> error type
     */
    interface Supplier<V, E extends Exception> {
        /**
         * Returns a value.
         * @return value
         * @throws E possibly checked error type
         */
        V get() throws E;
    }

    private Internal() { }
}
