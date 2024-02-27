package io.github.artkonr.result;

import lombok.NonNull;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link BaseResult result} container that carries no value.
 * @param <E> error type
 */
public class FlagResult<E extends Exception> extends BaseResult<E> {

    /**
     * Runs a specified {@link Wrap.Runnable}, catches an expected exception
     *  and returns it as a {@link FlagResult}.
     * <p>The expected exception can be any {@link Exception} type,
     *  this method internally checks if the caught exception type
     *  matches the expected type or its subtype. Normally, the client
     *  code should pass the narrowest type possible.
     * <p>If no error is thrown by the supplied function, the call
     *  resolves to {@code OK}.
     * @param errType expected type
     * @param action fallible action
     * @return result of the invocation
     * @param <E> error type
     * @throws IllegalArgumentException if either of the arguments not provided
     * @throws IllegalStateException if the exception during invocation is not
     *  of an expected type or its subtype
     */
    public static <E extends Exception> FlagResult<E> wrap(@NonNull Class<E> errType,
                                                           @NonNull Wrap.Runnable action) {
        try {
            action.run();
            return FlagResult.ok();
        } catch (Exception exception) {
            if (errType.isAssignableFrom(exception.getClass())) {
                @SuppressWarnings("unchecked")
                E cast = (E) exception;
                return FlagResult.err(cast);
            } else {
                throw BaseResult.unexpectedWrappedException(errType, exception);
            }
        }
    }

    /**
     * Creates a new instance from an existing {@link BaseResult result}.
     *  The {@code OK}/{@code ERR} state is taken from the source entity.
     * <p>Note: as {@link FlagResult} doesn't contain a value in {@code OK}
     *  state, any value carried by the source {@code OK} result is dropped.
     * @param source source {@link BaseResult result}
     * @return new instance
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <E extends Exception> FlagResult<E> from(@NonNull BaseResult<E> source) {
        if (source.isOk()) {
            return FlagResult.ok();
        } else {
            return FlagResult.err(source.error);
        }
    }

    /**
     * Creates an {@code OK} item with no value.
     * @return new {@code OK} instance
     * @param <E> error type
     */
    public static <E extends Exception> FlagResult<E> ok() {
        return new FlagResult<>(null);
    }

    /**
     * Creates an {@code ERR} item with the provided error.
     * @param error error
     * @return new {@code ERR} instance
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <E extends Exception> FlagResult<E> err(@NonNull E error) {
        return new FlagResult<>(error);
    }

    /**
     * Joins a {@link Collection} of {@link BaseResult any result} objects into
     *  a single {@link FlagResult}.
     * <p>The eventual {@link FlagResult} will have the {@code OK} state
     *  iff. all {@link BaseResult source results} are {@code OK}. Otherwise,
     *  the internal error of the fist occurring {@code ERR} result is taken.
     * <p>As {@link Collection} does not specify the ordering, the eventual
     *  {@link FlagResult} is not guaranteed to contain the same internal
     *  error state every time.
     * <p>Null-safe: should the input contain {@code null} items,
     *  this method will remove them.
     * @param results collection of {@link BaseResult results}
     * @return results joined into a {@link FlagResult}
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <E extends Exception> FlagResult<E> join(@NonNull Collection<BaseResult<E>> results) {
        return results.stream()
                .filter(Objects::nonNull)
                .filter(BaseResult::isErr)
                .findFirst()
                .map(errResult -> FlagResult.err(errResult.getErr()))
                .orElseGet(FlagResult::ok);
    }

    /**
     * Converts {@code this} {@link FlagResult} into a {@link Result
     *  value result} with a given item if {@code this} result is
     *  {@code OK}; simply carriers internal error state otherwise.
     * @param item item to populate the result with
     * @return new {@link Result} instance
     * @param <V> type of {@code OK} item
     * @throws IllegalArgumentException if no argument provided
     */
    public <V> Result<V, E> populate(@NonNull V item) {
        if (isOk()) {
            return Result.ok(item);
        } else {
            return Result.err(error);
        }
    }

    /**
     * Produces a new {@link FlagResult} out of {@code this}
     *  and another {@link FlagResult}.
     * <p>The eventual {@link FlagResult} will have {@code OK}
     *  state iff. both instances are {@code OK}; {@code ERR}
     *  state is assumed otherwise, with the error taken from
     *  the only {@code ERR} from the pair or from {@code this}
     *  instance if both are {@code ERR}.
     * <p>In order to be fuse-able, the fused items must contain
     *  the same error type.
     * @param another fuse with
     * @return a new {@link FlagResult}
     * @throws IllegalArgumentException if no argument provided
     */
    public FlagResult<E> fuse(@NonNull FlagResult<E> another) {
        if (isErr()) {
            return err(error);
        }

        if (another.isErr()) {
            return err(another.getErr());
        }

        return ok();
    }

    /**
     * {@inheritDoc}
     * @param remap callback
     * @return mapped {@link FlagResult}
     * @param <N> new error type
     * @throws IllegalArgumentException if no argument provided or
     *  if the callback function returns {@code null}
     */
    @Override
    public <N extends Exception> FlagResult<N> mapErr(@NonNull Function<E, N> remap) {
        if (isErr()) {
            return FlagResult.err(remap.apply(error));
        } else {
            return FlagResult.ok();
        }
    }

    /**
     * Converts an {@code OK} result into {@code ERR}
     *  using the specified factory if {@code this}
     *  instance is {@code OK}. Recreates {@code this}
     *  with its internal error state otherwise.
     * <p>Note: the resulting type parameter is up-cast
     *  to the most generic type supported: {@link Exception}.
     * @param factory exception factory
     * @return converted instance
     * @throws IllegalArgumentException if no argument provided or if
     *  the factory function returns {@code null}
     */
    @Override
    public FlagResult<Exception> taint(@NonNull Supplier<? extends Exception> factory) {
        if (isOk()) {
            return err(factory.get());
        } else {
            return err(error);
        }
    }

    /**
     * Wraps the internal {@code ERR} state into a {@link
     *  Failure wrapping exception} and throws if {@code
     *  this} instance is an {@code ERR}.
     * @throws Failure result wrapping exception
     */
    public void unwrap() {
        if (isErr()) {
            throw new Failure(error);
        }
    }

    /**
     * Builds a text representation.
     * @return text representation
     */
    @Override
    public String toString() {
        return "FlagResult[" + (isOk() ? "ok" : "err=" + error) + ']';
    }

    /**
     * Checks if {@code this} equals {@code that}.
     * @param that that
     * @return comparison result
     */
    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        FlagResult<?> result = (FlagResult<?>) that;

        return Objects.equals(error, result.error);
    }

    /**
     * Computes hashcode.
     * @return computed hashcode
     */
    @Override
    public int hashCode() {
        if (isOk()) {
            return 31;
        } else {
            return 31 * error.hashCode();
        }
    }

    /**
     * Internal constructor.
     * <p>Setting the argument to {@code null} must
     *  follow the class contract.
     * @param error nullable error value
     */
    protected FlagResult(E error) {
        super(error);
    }
}
