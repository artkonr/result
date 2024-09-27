package io.github.artkonr.result;

import lombok.NonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link BaseResult result} container that carries no value.
 * @param <E> error type
 */
public class FlagResult<E extends Exception> extends BaseResult<E> {

    /**
     * Runs a specified {@link Wrap.Runnable}, catches an expected exception
     *  and returns it as a {@link FlagResult}.
     * <p>The expected exception can be any {@link Exception} type.
     * <p>If no error is thrown by the supplied function, the call
     *  resolves to {@code OK}.
     * @param action fallible action
     * @return result of the invocation
     * @throws IllegalArgumentException if either of the arguments not provided
     */
    public static FlagResult<Exception> wrap(@NonNull Wrap.Runnable action) {
        try {
            action.run();
            return FlagResult.ok();
        } catch (Exception x) {
            return FlagResult.err(x);
        }
    }

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
     * Invokes {@link FlagResult}-producing functions in a serialized
     *  manner and short-circuits upon the first encountered {@code
     *  ERR} result. Encountered {@code null} elements are ignored.
     * <p>This method takes the input collection as-is, the invocations
     *  are made in the order they appear in the collection.
     * @param invocations operations to invoke
     * @return if either of the invocations short-circuits, an {@code ERR}
     *  result is returned. If all invocations succeed, an {@code OK} is returned.
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <E extends Exception> FlagResult<E> chain(@NonNull Collection<Supplier<BaseResult<E>>> invocations) {
        List<Supplier<BaseResult<E>>> filtered = invocations.stream()
                .filter(Objects::nonNull)
                .toList();
        if (!filtered.isEmpty()) {
            Iterator<Supplier<BaseResult<E>>> iterator = filtered.iterator();
            E err = null;
            while (iterator.hasNext()) {
                BaseResult<E> curr = iterator.next().get();
                if (curr != null && curr.isErr()) {
                    err = curr.getErr();
                    break;
                }
            }

            if (err == null) {
                return FlagResult.ok();
            } else {
                return err(err);
            }
        } else {
            return FlagResult.ok();
        }
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
        return join(results, TakeFrom.HEAD);
    }

    /**
     * Joins a {@link Collection} of {@link BaseResult any result} objects into
     *  a single {@link FlagResult}.
     * <p>The eventual {@link FlagResult} will have the {@code OK} state
     *  iff. all {@link BaseResult source results} are {@code OK}. Otherwise,
     *  the internal error of is taken as described by {@link TakeFrom}.
     * <p>As {@link Collection} does not specify the ordering, the eventual
     *  {@link FlagResult} is not guaranteed to contain the same internal
     *  error state every time.
     * <p>Null-safe: should the input contain {@code null} items,
     *  this method will remove them.
     * @param results collection of {@link BaseResult results}
     * @param rule fusing rule
     * @return results joined into a {@link FlagResult}
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <E extends Exception> FlagResult<E> join(@NonNull Collection<BaseResult<E>> results,
                                                           @NonNull TakeFrom rule) {
        List<BaseResult<E>> errored = results.stream()
                .filter(Objects::nonNull)
                .filter(BaseResult::isErr)
                .toList();
        if (!errored.isEmpty()) {
            BaseResult<E> picked = switch (rule) {
                case HEAD -> errored.get(0);
                case TAIL -> errored.get(errored.size() - 1);
            };
            return FlagResult.err(picked.error);
        } else {
            return FlagResult.ok();
        }
    }

    /**
     * Converts {@code this} {@link FlagResult} into a {@link Result
     *  value result} with a given item if {@code this} result is
     *  {@code OK}; simply carries internal error state otherwise.
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
     * Converts {@code this} {@link FlagResult} into a {@link Result
     *  value result} using a given factory if {@code this} result is
     *  {@code OK}; simply carries internal error state otherwise.
     * @param factory item factory to populate the result with
     * @return new {@link Result} instance
     * @param <V> type of {@code OK} item
     * @throws IllegalArgumentException if no argument provided or
     *  if the factory returns a {@code null} object
     */
    public <V> Result<V, E> populate(@NonNull Supplier<V> factory) {
        return populate(factory.get());
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
        return fuse(another, TakeFrom.HEAD);
    }

    /**
     * Produces a new {@link FlagResult} out of {@code this}
     *  and another {@link FlagResult}.
     * <p>The eventual {@link FlagResult} will have {@code OK}
     *  state iff. both instances are {@code OK}; {@code ERR}
     *  state is assumed otherwise, with the error taken from
     *  the only {@code ERR} from the pair. If both instances
     *  are {@code ERR}, the {@link TakeFrom rule arg} allows
     *  to point which of the {@code ERR} items passes the
     *  error on.
     * <p>In order to be fuse-able, the fused items must contain
     *  the same error type.
     * @param another fuse with
     * @param rule fusing rule
     * @return a new {@link FlagResult}
     * @throws IllegalArgumentException if no argument provided
     */
    public FlagResult<E> fuse(@NonNull FlagResult<E> another,
                              @NonNull TakeFrom rule) {
        return rule.takeError(this, another)
                .map(FlagResult::err)
                .orElseGet(FlagResult::ok);
    }

    /**
     * Invokes the supplied callback if {@code this}
     *  instance is {@code OK}.
     * @param consumer callback
     * @return same instance
     * @throws IllegalArgumentException if no argument provided
     */
    public FlagResult<E> peek(@NonNull Runnable consumer) {
        if (isOk()) {
            consumer.run();
        }
        return this;
    }

    /**
     * Inspects the {@code ERR} state using the specified function,
     *  if {@code this} instance is {@code ERR}.
     * @param consumer inspection function
     * @return {@code this} instance
     * @throws IllegalArgumentException if no argument provided
     */
    public FlagResult<E> peekErr(@NonNull Consumer<E> consumer) {
        if (isErr()) {
            consumer.accept(error);
        }

        return this;
    }

    /**
     * Inspects the {@code ERR} state using the specified function,
     *  if {@code this} instance is {@code ERR} and is of the specified type.
     * @param type expected type
     * @param consumer inspection function
     * @return {@code this} instance
     * @throws IllegalArgumentException if either of the arguments provided
     */
    public FlagResult<E> peekErr(@NonNull Class<? extends Exception> type,
                                 @NonNull Consumer<E> consumer) {
        if (isErrAnd(type)) {
            consumer.accept(error);
        }

        return this;
    }

    /**
     * Inspects the {@code ERR} state using the specified function,
     *  if {@code this} instance is {@code ERR} and the provided
     *  predicate holds.
     * @param predicate predicate
     * @param consumer inspection function
     * @return {@code this} instance
     * @throws IllegalArgumentException if either of the arguments provided
     */
    public FlagResult<E> peekErr(@NonNull Predicate<E> predicate,
                                 @NonNull Consumer<E> consumer) {
        if (isErrAnd(predicate)) {
            consumer.accept(error);
        }

        return this;
    }

    /**
     * Erases the {@code ERR} type information of
     *  {@code this} result.
     * @return result with broadened
     */
    @Override
    public FlagResult<Exception> upcast() {
        return isOk() ? FlagResult.ok() : FlagResult.err(error);
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
     * Applies the {@link BaseResult}-returning function to
     *  convert the item if {@code this} instance is {@code OK};
     *  returns a recreated {@code ERR} otherwise.
     * @param remap function
     * @return mapped {@link FlagResult}
     * @throws IllegalArgumentException if no argument provided or
     *  if the callback function returns {@code null}
     */
    public FlagResult<E> flatMap(@NonNull Supplier<FlagResult<E>> remap) {
        if (isErr()) {
            return err(error);
        } else {
            return returnRemapped(remap.get());
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
     * Converts an {@code OK} result into {@code ERR}
     *  using the specified factory if {@code this}
     *  instance is {@code OK}. Recreates {@code this}
     *  with its internal error state otherwise.
     * @param factory exception factory
     * @return converted instance
     * @throws IllegalArgumentException if no argument provided or if
     *  the factory function returns {@code null}
     */
    @Override
    public FlagResult<E> fork(@NonNull Supplier<E> factory) {
        if (isOk()) {
            return err(factory.get());
        } else {
            return err(error);
        }
    }

    /**
     * Unconditionally converts an {@code ERR} result into {@code OK}.
     * @return new {@code OK} result
     */
    public FlagResult<E> recover() {
        return FlagResult.ok();
    }

    /**
     * Conditionally converts an {@code ERR} result into {@code OK}
     *  if the supplied predicate holds.
     * <p>If the predicate does not hold, returns the recreated {@code OK} item.
     * @param condition checked predicate
     * @return new {@code OK} result
     * @throws IllegalArgumentException if the argument not provided
     */
    public FlagResult<E> recover(@NonNull Predicate<E> condition) {
        if (isOk()) {
            return FlagResult.ok();
        } else {
            return condition.test(error)
                    ? FlagResult.ok()
                    : FlagResult.err(error);
        }
    }

    /**
     * Conditionally converts an {@code ERR} result into {@code OK}
     *  if the {@code ERR} is {@code instanceof} the specified type.
     * @param ifType checked type
     * @return new {@code OK} result with recovery object
     * @throws IllegalArgumentException if the argument not provided
     */
    public FlagResult<E> recover(@NonNull Class<? extends E> ifType) {
        if (isOk()) {
            return FlagResult.ok();
        } else {
            if (ifType.isAssignableFrom(error.getClass())) {
                return FlagResult.ok();
            } else {
                return FlagResult.err(error);
            }
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
     * Throws the internal {@code ERR} state if {@code
     *  this} instance is an {@code ERR}.
     * @throws E result wrapping exception
     */
    public void unwrapChecked() throws E {
        if (isErr()) {
            throw error;
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

    private FlagResult<E> returnRemapped(@NonNull FlagResult<E> val) {
        return val;
    }
}
