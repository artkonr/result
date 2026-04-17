package io.github.artkonr.result;

/**
 * Functional interfaces for operations that throw checked exceptions.
 * <pre>{@code
 * Wrap.Supplier<String> supplier = () -> Files.readString(path);
 * Wrap.Runnable runnable = () -> Thread.sleep(1000);
 * }</pre>
 */
public interface Wrap {

    /**
     * An action that completes normally or throws a checked exception.
     * <p>Similar to {@link Runnable} but allows throwing any exception.
     * <pre>{@code
     * Wrap.Runnable action = () -> Thread.sleep(1000);
     * Result.wrap(() -> { action.run(); return null; });
     * }</pre>
     */
    @FunctionalInterface
    interface Runnable extends Internal.Runnable<Exception> { }

    /**
     * A supplier of values that may throw a checked exception.
     * <p>Similar to {@link java.util.function.Supplier} but allows throwing any exception.
     * <pre>{@code
     * Wrap.Supplier<String> supplier = () -> Files.readString(Paths.get("data.txt"));
     * Result<String, Exception> result = Result.wrap(supplier);
     * }</pre>
     * @param <V> value type
     */
    @FunctionalInterface
    interface Supplier<V> extends Internal.Supplier<V, Exception> { }
}
