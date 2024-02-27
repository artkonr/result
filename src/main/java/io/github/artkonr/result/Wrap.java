package io.github.artkonr.result;

/**
 * A collection of interfaces that wrap lambda interfaces,
 *  but allow to use checked exceptions.
 */
public interface Wrap {

    /**
     * A checked-exception-safe version of {@link java.lang.Runnable}.
     */
    @FunctionalInterface
    interface Runnable extends Internal.Runnable<Exception> { }

    /**
     * A checked-exception-safe version of {@link java.util.function.Supplier}.
     * @param <V> value type
     */
    @FunctionalInterface
    interface Supplier<V> extends Internal.Supplier<V, Exception> { }
}
