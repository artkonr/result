package io.github.artkonr.result;

import lombok.NonNull;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An abstract result container that suggests that there
 *  is an operation that concludes OK or as an error. Suited
 *  for catching and propagating errors in a functional style.
 * <p>Two principal states are managed by this entity:
 * <ol>
 *     <li>{@code OK} - stands for a successful completion;</li>
 *     <li>{@code ERR} - stands for an errored completion.</li>
 * </ol>
 * <p>Implementations decide what constitutes an {@code OK} result,
 *  but the {@code ERR} result is enforced by this class: an error
 *  result is always backed by a generified {@link Exception}.
 * @param <E> type of exception held by the ERR result.
 */
public abstract class BaseResult<E extends Exception> {

    /**
     * Contained error state.
     * <p>Internal implementation detail: following the previous point,
     *  this class and subclasses decide if an instance stands for
     *  {@code OK} or {@code ERR} based
     */
    protected final E error;

    /**
     * Checks if {@code this} instance is {@code OK}.
     * @return {@code true} if {@code this} is an {@code OK} result
     */
    public final boolean isOk() {
        return error == null;
    }

    /**
     * Checks if {@code this} instance is {@code ERR}.
     * @return {@code true} if {@code this} is an {@code ERR} result
     */
    public final boolean isErr() {
        return error != null;
    }

    /**
     * Checks if {@code this} instance is {@code ERR}
     *  and the underlying error is of the specified
     *  type or its subclass.
     * @param type expected type
     * @return {@code true} if {@code this} is an {@code ERR}
     *  result and the predicate holds
     * @throws IllegalArgumentException if no argument provided
     */
    public final boolean isErrAnd(@NonNull Class<? extends Exception> type) {
        return isErr() && type.isAssignableFrom(error.getClass());
    }

    /**
     * Checks if {@code this} instance is {@code ERR}
     *  and the specified predicate holds.
     * @param predicate predicate
     * @return {@code true} if {@code this} is an {@code ERR}
     *  result and the predicate holds
     * @throws IllegalArgumentException if no argument provided
     */
    public final boolean isErrAnd(@NonNull Predicate<E> predicate) {
        return isErr() && predicate.test(error);
    }

    /**
     * Attempts to get container {@code ERR} state.
     * @return internal {@code ERR} state
     * @throws IllegalStateException if {@code this} instance is not an {@code ERR} result.
     */
    public final E getErr() {
        if (isErr()) {
            return error;
        } else {
            throw new IllegalStateException("not an ERR result");
        }
    }

    /**
     * Applies a callback function to convert the internal
     *  error state if {@code this} instance is {@code ERR};
     *  returns a new {@code OK} otherwise.
     * @param remap callback
     * @return mapped result
     * @param <N> new error type
     * @throws IllegalArgumentException if no argument provided or
     *  if the callback function returns {@code null}
     */
    public abstract <N extends Exception> BaseResult<N> mapErr(@NonNull Function<E, N> remap);

    /**
     * Converts an {@code OK} result into {@code ERR}
     *  using the specified factory if {@code this}
     *  instance is {@code OK}. Recreates {@code this}
     *  with its internal error state otherwise.
     * <p>Note: the resulting type parameter is up-cast
     *  to the most generic type supported: {@link Exception}.
     * @param factory exception factory
     * @return converted result
     */
    public abstract BaseResult<Exception> taint(@NonNull Supplier<? extends Exception> factory);

    /**
     * Converts an {@code OK} result into {@code ERR}
     *  using the specified factory if {@code this}
     *  instance is {@code OK}. Recreates {@code this}
     *  with its internal error state otherwise.
     * @param factory exception factory
     * @return converted result
     */
    public abstract BaseResult<E> taintMatching(@NonNull Supplier<E> factory);

    /**
     * Performs the specified callback if {@code this}
     *  instance is {@code ERR} or does nothing otherwise.
     * @param action callback
     * @throws IllegalArgumentException if the argument is null
     */
    public void ifOk(@NonNull Runnable action) {
        if (isOk()) {
            action.run();
        }
    }

    /**
     * Performs the specified callback if {@code this}
     *  instance is {@code ERR} or does nothing otherwise.
     * @param action callback
     * @throws IllegalArgumentException if the argument is null
     */
    public void ifErr(@NonNull Runnable action) {
        if (isErr()) {
            action.run();
        }
    }

    /**
     * Performs the specified callback if {@code this}
     *  instance is {@code ERR} or does nothing otherwise.
     * @param action callback
     * @throws IllegalArgumentException if the argument is null
     */
    public void ifErr(@NonNull Consumer<E> action) {
        if (isErr()) {
            action.accept(error);
        }
    }

    /**
     * Exception factory: creates an exception that notifies of
     *  an unexpected exception during wrapping.
     * @param expected expected type
     * @param err actual error caught
     * @return created exception
     */
    protected static IllegalStateException unexpectedWrappedException(Class<?> expected,
                                                                      Exception err) {
        return new IllegalStateException(String.format(
                "unexpected wrapped exception: expected=%s actual=%s",
                expected.getName(),
                err
        ));
    }

    /**
     * Internal constructor.
     * <p>Setting the argument to {@code null} must
     *  follow the class contract.
     * @param error nullable error value
     */
    protected BaseResult(E error) {
        this.error = error;
    }
}
