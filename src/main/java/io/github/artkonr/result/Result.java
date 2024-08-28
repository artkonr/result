package io.github.artkonr.result;

import lombok.NonNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link BaseResult result} that contains a value (item) that is
 *  associated with the {@code OK} state.
 * @param <V> item type
 * @param <E> error type
 */
public class Result<V, E extends Exception> extends BaseResult<E> {

    /**
     * Internally stored {@code OK} value. Not null.
     */
    protected final V item;

    /**
     * Runs a specified {@link Wrap.Supplier}, catches an expected exception
     *  and returns it as a {@link Result}.
     * <p>The expected exception can be any {@link Exception} type.
     * <p>If no error is thrown by the supplied function, the call
     *  resolves to {@code OK}.
     * @param action fallible action
     * @return result of the invocation
     * @param <V> item type
     * @throws IllegalArgumentException if either of the arguments not provided
     *  or if the action returns a {@code null} value
     */
    public static <V> Result<V, Exception> wrap(@NonNull Wrap.Supplier<V> action) {
        V val;
        try {
            val = action.get();
        } catch (Exception ex) {
            return Result.err(ex);
        }

        return Result.ok(val);
    }

    /**
     * Runs a specified {@link Wrap.Supplier}, catches an expected exception
     *  and returns it as a {@link Result}.
     * <p>The expected exception can be any {@link Exception} type,
     *  this method internally checks if the caught exception type
     *  matches the expected type or its subtype. Normally, the client
     *  code should pass the narrowest type possible.
     * <p>If no error is thrown by the supplied function, the call
     *  resolves to {@code OK}.
     * @param errType expected type
     * @param action fallible action
     * @return result of the invocation
     * @param <V> item type
     * @param <E> error type
     * @throws IllegalArgumentException if either of the arguments not provided
     *  or if the action returns a {@code null} value
     * @throws IllegalStateException if the exception during invocation is not
     *  of an expected type or its subtype
     */
    public static <V, E extends Exception> Result<V, E> wrap(@NonNull Class<E> errType,
                                                             @NonNull Wrap.Supplier<@NonNull V> action) {
        V item;
        try {
            item = action.get();
        } catch (Exception exception) {
            if (errType.isAssignableFrom(exception.getClass())) {
                @SuppressWarnings("unchecked")
                E cast = (E) exception;
                return Result.err(cast);
            } else {
                throw BaseResult.unexpectedWrappedException(errType, exception);
            }
        }

        return Result.ok(item);
    }

    /**
     * Creates a new instance from an existing {@link Result result}.
     *  The {@code OK}/{@code ERR} state is taken from the source entity.
     * <p>Note: as {@link Result} must contain an item in {@code OK}
     *  state, only {@link Result} is allowed as input.
     * @param source source {@link BaseResult result}
     * @return new instance
     * @param <V> item type
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <V, E extends Exception> Result<V, E> from(@NonNull Result<V, E> source) {
        if (source.isOk()) {
            return Result.ok(source.item);
        } else {
            return Result.err(source.error);
        }
    }

    /**
     * Creates an {@code OK} item with item.
     * @return new {@code OK} instance
     * @param item ok item
     * @param <V> item type
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <V, E extends Exception> Result<V, E> ok(@NonNull V item) {
        return new Result<>(item, null);
    }

    /**
     * Creates an {@code ERR} item with the provided error.
     * @param error error
     * @return new {@code ERR} instance
     * @param <V> item type
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <V, E extends Exception> Result<V, E> err(@NonNull E error) {
        return new Result<>(null, error);
    }

    /**
     * Joins a {@link Collection} of {@link Result} objects into
     *  a {@link Result} of {@link List} of items.
     * <p>The eventual {@link Result} will have the {@code OK} state
     *  iff. all {@link Result source results} are {@code OK}. Otherwise,
     *  the internal error of the fist occurring {@code ERR} result is taken.
     * <p>As {@link Collection} does not specify the ordering, the eventual
     *  {@link Result} is not guaranteed to contain the same internal
     *  error state every time.
     * <p>Null-safe: should the input contain {@code null} items,
     *  this method will remove them.
     * @param results collection of {@link Result results}
     * @return results joined into a {@link Result}
     * @param <V> item type
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <V, E extends Exception> Result<List<V>, E> join(@NonNull Collection<Result<V, E>> results) {
        return join(results, TakeFrom.HEAD);
    }

    /**
     * Joins a {@link Collection} of {@link Result} objects into
     *  a {@link Result} of {@link List} of items.
     * <p>The eventual {@link Result} will have the {@code OK} state
     *  iff. all {@link Result source results} are {@code OK}. Otherwise,
     *  the internal error of is taken as described by {@link TakeFrom}.
     * <p>As {@link Collection} does not specify the ordering, the eventual
     *  {@link Result} is not guaranteed to contain the same internal
     *  error state every time.
     * <p>Null-safe: should the input contain {@code null} items,
     *  this method will remove them.
     * @param results collection of {@link Result results}
     * @param rule fusing rule
     * @return results joined into a {@link Result}
     * @param <V> item type
     * @param <E> error type
     * @throws IllegalArgumentException if no argument provided
     */
    public static <V, E extends Exception> Result<List<V>, E> join(@NonNull Collection<Result<V, E>> results,
                                                                   @NonNull TakeFrom rule) {
        List<Result<V, E>> nonNull = results.stream()
                .filter(Objects::nonNull)
                .toList();
        List<Result<V, E>> errored = nonNull.stream()
                .filter(BaseResult::isErr)
                .toList();
        if (!errored.isEmpty()) {
            Result<V, E> result = switch (rule) {
                case HEAD -> errored.get(0);
                case TAIL -> errored.get(errored.size() - 1);
            };
            return Result.err(result.error);
        } else {
            return Result.ok(nonNull.stream()
                    .map(result -> result.item)
                    .toList());
        }
    }

    /**
     * Takes an {@link Optional} out of an {@code OK} {@link Result}
     *  and wraps the said {@link Result} into an {@link Optional}.
     * <p>The wrapped {@link Result} retains the state of the input.
     * <p>The wrapping {@link Optional} retains the state of the input.
     * @param result {@link Result} bearing an {@link Optional} object to elevate
     * @return {@link Result} wrapped in {@link Optional}
     * @param <V> item type
     * @param <E> error type
     */
    public static <V, E extends Exception> Optional<Result<V, E>> elevate(@NonNull Result<Optional<V>, E> result) {
        if (result.isOk()) {
            return result.get().map(Result::ok);
        } else {
            return Optional.of(Result.err(result.error));
        }
    }

    /**
     * Checks if {@code this} instance is {@code OK}
     *  and the specified predicate holds.
     * @param predicate predicate
     * @return {@code true} if {@code this} is an {@code OK}
     *  result and the predicate holds
     * @throws IllegalArgumentException if no argument provided
     */
    public boolean isOkAnd(@NonNull Predicate<V> predicate) {
        return isOk() && predicate.test(item);
    }

    /**
     * Attempts to get {@code OK} state or throws
     *  if {@code this} instance is {@code ERR}.
     * @return {@code OK} item
     * @throws IllegalStateException if {@code this}
     *  instance is {@code ERR}
     */
    public V get() {
        if (isOk()) {
            return item;
        }

        throw new IllegalStateException("not an OK result");
    }

    /**
     * Produces a new {@link Result} out of {@code this}
     *  and another {@link Result} by combining their
     *  items into a {@link Fuse}.
     * <p>The eventual {@link Result} will have {@code OK}
     *  state iff. both instances are {@code OK}; {@code ERR}
     *  state is assumed otherwise, with the error taken from
     *  the only {@code ERR} from the pair or from {@code this}
     *  instance if both are {@code ERR}.
     * <p>In order to be fuse-able, fused items must contain
     *  the same error type.
     * @param another fuse with
     * @param <N> fused item type
     * @return a new {@link Result} containing {@link Fuse}
     * @throws IllegalArgumentException if no argument provided
     */
    public <N> Result<Fuse<V, N>, E> fuse(@NonNull Result<N, E> another) {
        return fuse(another, TakeFrom.HEAD);
    }

    /**
     * Produces a new {@link Result} out of {@code this}
     *  and another {@link Result} by combining their
     *  items into a {@link Fuse}.
     * <p>The eventual {@link Result} will have {@code OK}
     *  state iff. both instances are {@code OK}; {@code ERR}
     *  state is assumed otherwise with the error taken from
     *  the only {@code ERR} from the pair. If both instances
     *  are {@code ERR}, the {@link TakeFrom rule arg} allows
     *  to point which of the {@code ERR} items passes the
     *  error on.
     * <p>In order to be fuse-able, fused items must contain
     *  the same error type.
     * @param another fuse with
     * @param rule fusing rule
     * @param <N> fused item type
     * @return a new {@link Result} containing {@link Fuse}
     * @throws IllegalArgumentException if either of the arguments not provided
     */
    public <N> Result<Fuse<V, N>, E> fuse(@NonNull Result<N, E> another,
                                          @NonNull TakeFrom rule) {
        return rule.takeError(this, another)
                .<Result<Fuse<V, N>, E>>map(Result::err)
                .orElseGet(() -> Result.ok(new Fuse<>(this.item, another.item)));
    }

    /**
     * Produces a new {@link Result} out of {@code this}
     *  and {@link BaseResult any other result}.
     * <p>The eventual {@link Result} will have {@code OK}
     *  state iff. both instances are {@code OK}; {@code ERR}
     *  state is assumed otherwise, with the error taken from
     *  the only {@code ERR} from the pair or from {@code this}
     *  instance if both are {@code ERR}.
     * <p>As source {@link BaseResult result} may or may not
     *  contain an item (contrary to {@link Result#fuse(Result)
     *  the other implementation}), the {@code OK} result is
     *  always resolved using item belonging to {@code this} instance.
     * <p>In order to be fuse-able, fused items must contain
     *  the same error type.
     * @param another fuse with
     * @return a new {@link FlagResult}
     * @throws IllegalArgumentException if no argument provided
     */
    public Result<V, E> fuse(@NonNull BaseResult<E> another) {
        return TakeFrom.HEAD.takeError(this, another)
                .<Result<V, E>>map(Result::err)
                .orElseGet(() -> Result.ok(this.item));
    }

    /**
     * Produces a new {@link Result} out of {@code this}
     *  and {@link BaseResult any other result}.
     * <p>The eventual {@link Result} will have {@code OK}
     *  state iff. both instances are {@code OK}; {@code ERR}
     *  state is assumed otherwise, with the error taken from
     *  the only {@code ERR} from the pair. If both instances
     *  are {@code ERR}, the {@link TakeFrom rule arg} allows
     *  to point which of the {@code ERR} items passes the
     *  error on.
     * <p>As source {@link BaseResult result} may or may not
     *  contain an item (contrary to {@link Result#fuse(Result)
     *  the other implementation}), the {@code OK} result is
     *  always resolved using item belonging to {@code this} instance.
     * <p>In order to be fuse-able, fused items must contain
     *  the same error type.
     * @param another fuse with
     * @param rule fusing rule
     * @return a new {@link FlagResult}
     * @throws IllegalArgumentException if either of the arguments not provided
     */
    public Result<V, E> fuse(@NonNull BaseResult<E> another,
                             @NonNull TakeFrom rule) {
        return rule.takeError(this, another)
                .<Result<V, E>>map(Result::err)
                .orElseGet(() -> Result.ok(this.item));
    }

    /**
     * Inspects the {@code OK} state using the specified function,
     *  if {@code this} instance is {@code OK}.
     * @param consumer inspection function
     * @return {@code this} instance
     * @throws IllegalArgumentException if no argument provided
     */
    public Result<V, E> peek(@NonNull Consumer<V> consumer) {
        if (isOk()) {
            consumer.accept(item);
        }

        return this;
    }

    /**
     * Inspects the {@code OK} state using the specified function,
     *  if {@code this} instance is {@code OK} and is of the specified type.
     * @param predicate predicate
     * @param consumer inspection function
     * @return {@code this} instance
     * @throws IllegalArgumentException if either of the arguments provided
     */
    public Result<V, E> peek(@NonNull Predicate<V> predicate,
                             @NonNull Consumer<V> consumer) {
        if (isOkAnd(predicate)) {
            consumer.accept(item);
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
    public Result<V, E> peekErr(@NonNull Consumer<E> consumer) {
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
    public Result<V, E> peekErr(@NonNull Class<? extends Exception> type,
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
    public Result<V, E> peekErr(@NonNull Predicate<E> predicate,
                                @NonNull Consumer<E> consumer) {
        if (isErrAnd(predicate)) {
            consumer.accept(error);
        }

        return this;
    }

    /**
     * Converts {@code this} instance into a {@link FlagResult}
     *  by dropping the {@code OK} item. Internal error state
     *  is carried over as-is.
     * @return new {@link FlagResult}
     */
    public FlagResult<E> drop() {
        if (isErr()) {
            return FlagResult.err(error);
        } else {
            return FlagResult.ok();
        }
    }

    /**
     * A shorthand for {@link Result#map(Function)}.
     * @param item replacement
     * @return new {@code OK} {@link Result} with new item
     * @param <N> new item type
     */
    public <N> Result<N, E> swap(@NonNull N item) {
        return map(dropped -> item);
    }

    /**
     * Applies a callback function to convert the item
     *  if {@code this} instance is {@code OK};
     *  returns a new {@code ERR} otherwise.
     * <p>Very similar to {@link Optional#map(Function)}.
     * @param remap callback
     * @return mapped {@link Result}
     * @param <N> new item type
     * @throws IllegalArgumentException if no argument provided or
     *  if the callback function returns {@code null}
     */
    public <N> Result<N, E> map(@NonNull Function<V, N> remap) {
        if (isErr()) {
            return err(error);
        } else {
            return ok(remap.apply(item));
        }
    }

    /**
     * Applies a {@link Result}-returning callback function
     *  to convert the item if {@code this} instance is {@code OK};
     *  returns a new {@code ERR} otherwise.
     * <p>Very similar to {@link Optional#flatMap(Function)}.
     * @param remap callback
     * @return mapped {@link Result}
     * @param <N> new item type
     * @throws IllegalArgumentException if no argument provided or
     *  if the callback function returns {@code null}
     */
    public <N> Result<N, E> flatMap(@NonNull Function<V, Result<N, E>> remap) {
        if (isErr()) {
            return err(error);
        } else {
            return returnRemapped(remap.apply(item));
        }
    }

    /**
     * {@inheritDoc}
     * @param remap callback
     * @return mapped {@link Result}
     * @param <N> new error type
     * @throws IllegalArgumentException if no argument provided or
     *  if the callback function returns {@code null}
     */
    @Override
    public <N extends Exception> Result<V, N> mapErr(@NonNull Function<E, N> remap) {
        if (isErr()) {
            return err(remap.apply(error));
        } else {
            return ok(item);
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
    public Result<V, Exception> taint(@NonNull Supplier<? extends Exception> factory) {
        if (isOk()) {
            return err(factory.get());
        } else {
            return err(error);
        }
    }

    /**
     * Converts an {@code OK} result into {@code ERR}
     *  using the specified factory if {@code this}
     *  instance is {@code OK} and if the provided
     *  {@link Predicate} invoked on the item holds.
     * <p>If the predicate does not hold, returns the
     *  recreated {@code OK} item.
     * <p>If {@code this} is {@code ERR}, recreates itself
     *  with the internal error state.
     * <p>Note: the resulting type parameter is up-cast
     *  to the most generic type supported: {@link Exception}.
     * @param condition conversion predicate
     * @param factory exception factory
     * @return converted instance
     * @throws IllegalArgumentException if either of the arguments not
     *  provided or if the factory function returns {@code null}
     */
    public Result<V, Exception> taint(@NonNull Predicate<V> condition,
                                      @NonNull Function<V, ? extends Exception> factory) {
        if (isOk()) {
            if (condition.test(item)) {
                return err(factory.apply(item));
            } else {
                return ok(item);
            }
        } else {
            return err(error);
        }
    }

    /**
     * Converts an {@code ERR} result into {@code OK}
     *  using the specified factory faction. If {@code this}
     *  result is already an {@code OK} result, the internal
     *  {@code OK} object is returned.
     * <p>Equivalent to {@link Result#unwrapOr(Supplier)},
     *  except that this method returns a result.
     * @param factory {@code OK} object factory
     * @return new {@code OK} result with recovery object
     * @throws IllegalArgumentException if either of the arguments not
     *  provided or if the factory function returns {@code null}
     */
    public Result<V, E> recover(@NonNull Function<E, V> factory) {
        if (isOk()) {
            return Result.ok(item);
        } else {
            return Result.ok(factory.apply(error));
        }
    }

    /**
     * Converts an {@code ERR} result into {@code OK}
     *  using the specified factory faction. If {@code this}
     *  result is already an {@code OK} result, the internal
     *  {@code OK} object is returned.
     * <p>If the predicate does not hold, returns the
     *  recreated {@code OK} item.
     * @param condition checked predicate
     * @param factory {@code OK} object factory
     * @return new {@code OK} result with recovery object
     * @throws IllegalArgumentException if either of the arguments not
     *  provided or if the factory function returns {@code null}
     */
    public Result<V, E> recover(@NonNull Predicate<E> condition,
                                @NonNull Function<E, V> factory) {
        if (isOk()) {
            return Result.ok(item);
        } else {
            return condition.test(error)
                    ? Result.ok(factory.apply(error))
                    : Result.err(error);
        }
    }

    /**
     * Converts an {@code ERR} result into {@code OK}
     *  using the specified factory faction. If {@code this}
     *  result is already an {@code OK} result, the internal
     *  {@code OK} object is returned.
     * <p>If the predicate does not hold, returns the
     *  recreated {@code OK} item.
     * @param ifType checked type
     * @param factory {@code OK} object factory
     * @return new {@code OK} result with recovery object
     * @throws IllegalArgumentException if either of the arguments not
     *  provided or if the factory function returns {@code null}
     */
    public Result<V, E> recover(@NonNull Class<? extends E> ifType,
                                @NonNull Function<E, V> factory) {
        if (isOk()) {
            return Result.ok(item);
        } else {
            if (ifType.isAssignableFrom(error.getClass())) {
                return Result.ok(factory.apply(error));
            } else {
                return Result.err(error);
            }
        }
    }

    /**
     * Performs the specified callback if {@code this}
     *  instance is {@code OK} or does nothing otherwise.
     * @param action callback
     * @throws IllegalArgumentException if the argument is null
     */
    public void ifOk(@NonNull Consumer<V> action) {
        if (isOk()) {
            action.accept(item);
        }
    }

    /**
     * A safe take on {@link Result#get()}: if {@code this}
     *  is {@code ERR}, returns a provided fallback value.
     * <p>Similar to {@link Optional#orElse(Object)}.
     * @param another fallback
     * @return item or fallback
     * @throws IllegalArgumentException if no argument provided
     */
    public V unwrapOr(@NonNull V another) {
        if (isOk()) {
            return item;
        } else {
            return another;
        }
    }

    /**
     * A safe take on {@link Result#get()}: if {@code this}
     *  is {@code ERR}, returns a provided fallback value.
     * <p>Similar to {@link Optional#orElseGet(Supplier)}.
     * @param factory fallback factory
     * @return item or fallback
     * @throws IllegalArgumentException if no argument provided
     *  or if the provided factory returns {@code null}
     */
    public V unwrapOr(@NonNull Supplier<V> factory) {
        if (isOk()) {
            return item;
        } else {
            return returnSupplied(factory.get());
        }
    }

    /**
     * Returns {@code OK} item if {@code this} instance
     *  is an {@code OK} result. Wraps the internal {@code ERR}
     *  state into a {@link Failure wrapping exception} and
     *  throws otherwise.
     * @return item
     * @throws Failure result wrapping exception
     */
    public V unwrap() {
        if (isOk()) {
            return item;
        } else {
            throw new Failure(error);
        }
    }

    @Override
    public String toString() {
        return "Result[" + (isOk() ? "ok=" + item : "err=" + error) + ']';
    }

    /**
     * Checks if {@code this} equals {@code that}.
     * @param o that
     * @return comparison result
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Result<?, ?> result = (Result<?, ?>) o;

        if (!Objects.equals(item, result.item)) return false;
        return Objects.equals(error, result.error);
    }

    /**
     * Computes hashcode.
     * @return computed hashcode
     */
    @Override
    public int hashCode() {
        if (isOk()) {
            return 31 * item.hashCode();
        } else {
            return 31 * error.hashCode();
        }
    }

    /**
     * A pair-like record.
     * @param left left value
     * @param right right value
     * @param <L> left value type
     * @param <R> right value type
     */
    public record Fuse<L, R>(@NonNull L left,
                             @NonNull R right) { }

    /**
     * Internal constructor.
     * <p>Setting the arguments to {@code null} must
     *  follow the class contract.
     * @param item nullable ok value
     * @param error nullable error value
     */
    protected Result(V item, E error) {
        super(error);
        this.item = item;
    }

    private <N> Result<N, E> returnRemapped(@NonNull Result<N, E> val) {
        return val;
    }

    private V returnSupplied(@NonNull V val) {
        return val;
    }

}
