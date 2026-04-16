package io.github.artkonr.result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.NonNull;

/**
 * An abstract result container that suggests that there
 *  is an operation that concludes OK or with an error. Suited
 *  for catching and propagating errors in a functional style,
 *  but also offers a traditional "check-state-then-get" API
 *  as well as pattern-matching-friendly {@code sealed} subtypes:
 * <ol>
 *  <li>{@link Ok} - stands for a successful completion;</li>
 *  <li>{@link Err} - stands for an errored completion.</li>
 * </ol>
 * <p>Implementation detail: the API consists of the following
 *  kinds of operations:
 * <ol>
 *  <li>checking state;</li>
 *  <li>extracting state directly;</li>
 *  <li>modifying state; this operation always results in a new allocation</li>
 *  <li>branching from OK to ERR and back; this operation only allocates if the branching takes place</li>
 * </ol>
 * @param <V> type of value held by the OK result.
 * @param <E> type of exception held by the ERR result.
 */
public sealed interface Result<V, E extends Exception> permits Ok, Err {
  
  /**
   * Runs a specified {@link Wrap.Supplier}, catches an expected exception
   *  and returns it as a {@link Result}.
   * <p>The expected exception can be any {@link Exception} type.
   * <p>If no error is thrown by the supplied function, the call
   *  resolves to {@code OK}.
   * @param fn fallible action
   * @return result of the invocation
   * @param <V> item type
   * @throws IllegalArgumentException if either of the arguments not provided
   *  or if the action returns a {@code null} value
   */
  public static <V> Result<V, Exception> wrap(@NonNull Wrap.Supplier<V> fn) {
    V val;
    try {
        val = fn.get();
    } catch (Exception ex) {
        return new Err<>(ex);
    }

    return new Ok<>(val);
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
   * @param fn fallible action
   * @return result of the invocation
   * @param <V> item type
   * @param <E> error type
   * @throws IllegalArgumentException if either of the arguments not provided
   *  or if the action returns a {@code null} value
   * @throws IllegalStateException if the exception during invocation is not
   *  of an expected type or its subtype
   */
  public static <V, E extends Exception> Result<V, E> wrap(
    @NonNull Class<E> errType,
    @NonNull Wrap.Supplier<@NonNull V> fn
  ) {
      V item;
      try {
          item = fn.get();
      } catch (Exception exception) {
          if (errType.isAssignableFrom(exception.getClass())) {
              @SuppressWarnings("unchecked")
              E cast = (E) exception;
              return new Err<>(cast);
          } else {
              throw new IllegalStateException(
                "unexpected wrapped exception: expected=%s actual=%s".formatted(
                  errType.getName(),
                  exception.getClass().getName()
                )
              );
          }
      }

      return new Ok<>(item);
  }
  
  /**
   * Creates a new instance from an existing {@link Result result}.
   *  The {@code OK}/{@code ERR} state is taken from the source entity.
   * <p>Note: as {@link Result} must contain an item in {@code OK}
   *  state, only {@link Result} is allowed as input.
   * @param source source {@link Result result}
   * @return new instance
   * @param <V> item type
   * @param <E> error type
   * @throws IllegalArgumentException if no argument provided
   */
  public static <V, E extends Exception> Result<V, E> from(@NonNull Result<V, E> source) {
    return switch (source) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(item);
    };
  }
  
  /**
   * Invokes {@link Result}-producing functions in a serialized
   *  manner and short-circuits upon the first encountered {@code
   *  ERR} result. Encountered {@code null} elements are ignored.
   * <p>This method takes the input collection as-is, the invocations
   *  are made in the order they appear in the collection.
   * @param invocations operations to invoke
   * @return if either of the invocations short-circuits, an {@code ERR}
   *  result is returned. If all invocations succeed, a collection
   *  of resulting items is returned.
   * @param <V> item type
   * @param <E> error type
   * @throws IllegalArgumentException if no argument provided
   */
  public static <V, E extends Exception> Result<List<V>, E> chain(@NonNull Collection<Supplier<Result<V, E>>> invocations) {
    List<Supplier<Result<V, E>>> filtered = invocations.stream()
      .filter(it -> it != null)
      .toList();
    List<V> ok = new ArrayList<>();
    if (!filtered.isEmpty()) {
        Iterator<Supplier<Result<V, E>>> iterator = filtered.iterator();
        E err = null;
        while (iterator.hasNext()) {
            Result<V, E> curr = iterator.next().get();
            
            if (curr == null) {
                continue;
            }

            if (curr.isOk()) {
                ok.add(curr.value());
            } else {
                err = curr.err();
                break;
            }
        }

        if (err == null) {
            return new Ok<>(ok);
        } else {
            return new Err<>(err);
        }
    } else {
        return new Ok<>(ok);
    }
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
      .filter(it -> it != null)
      .toList();
    List<Result<V, E>> errored = nonNull.stream()
      .filter(it -> it.isErr())
      .toList();
    if (!errored.isEmpty()) {
        Result<V, E> result = switch (rule) {
            case HEAD -> errored.getFirst();
            case TAIL -> errored.getLast();
        };
        return new Err<>(result.err());
    } else {
        return new Ok<>(nonNull.stream()
          .map(result -> result.value())
          .toList()
        );
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
    return switch (result) {
      case Ok(var item) -> item.map(it -> new Ok<>(it));
      case Err(var item) -> Optional.of(new Err<>(item));
    };
  }
  
  /**
   * Checks if {@code this} instance is {@code OK}.
   * @return {@code true} if {@code this} is an {@code OK} result
   */
  default boolean isOk() {
    return this instanceof Ok;
  }
  
  /**
   * Checks if {@code this} instance is {@code OK}
   *  and the specified predicate holds.
   * @param cond predicate
   * @return {@code true} if {@code this} is an {@code OK}
   *  result and the predicate holds
   * @throws IllegalArgumentException if no argument provided
   */
  default boolean isOkAnd(@NonNull Predicate<V> cond) {
    return switch (this) {
      case Ok(var item) -> cond.test(item);
      case Err(var ignored) -> false;
    };
  }
  
  /**
   * Checks if {@code this} instance is {@code ERR}.
   * @return {@code true} if {@code this} is an {@code ERR} result
   */
  default boolean isErr() {
    return this instanceof Err;
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
  default boolean isErrAnd(@NonNull Class<? extends Exception> type) {
    if (this instanceof Err err) {
      return type.isAssignableFrom(err.item().getClass());
    } else {
      return false;
    }
  }
  
  /**
   * Checks if {@code this} instance is {@code ERR}
   *  and the specified predicate holds.
   * @param cond predicate
   * @return {@code true} if {@code this} is an {@code ERR}
   *  result and the predicate holds
   * @throws IllegalArgumentException if no argument provided
   */
  default boolean isErrAnd(@NonNull Predicate<E> cond) {
    return switch (this) {
      case Err(var item) when cond.test(item) -> true;
      default -> false;
    };
  }
  
  /**
   * Applies a callback function to convert the item
   *  if {@code this} instance is {@code OK};
   *  returns a new {@code ERR} otherwise.
   * <p>Very similar to {@link Optional#map(Function)}.
   * @param remap callback
   * @return mapped {@link LResult}
   * @param <N> new item type
   * @throws IllegalArgumentException if no argument provided or
   *  if the callback function returns {@code null}
   */
  default <N> Result<N, E> map(@NonNull Function<V, N> fn) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(fn.apply(item));
      case Err(var ignored) -> new Err<>(ignored);
    };
  }
  
  /**
   * A shorthand for {@link Result#map(Function)}.
   * @param item replacement
   * @return new {@code OK} {@link Result} with new item
   * @param <N> new item type
   */
  default <N> Result<N, E> swap(@NonNull N item) {
    return map(ignored -> item);
  }
  
  /**
   * A shorthand for {@link Result#map(Function)}.
   * @param fn replacement provider
   * @return new {@code OK} {@link Result} with new item
   * @param <N> new item type
   */
  default <N> Result<N, E> swap(@NonNull Supplier<N> item) {
    return swap(Remap.returnSupplied(item.get()));
  }
  
  default <N extends Exception> Result<V, N> stack(@NonNull N repl) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(repl);
    };
  }
  
  default <N extends Exception> Result<V, N> stack(@NonNull Function<E, N> fn) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(fn.apply(item));
    };
  }
  
  default <N extends Exception> Result<V, N> stack(@NonNull Supplier<N> fn) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(fn.get());
    };
  }
  
  default Result<V, Exception> upcast() {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(item);
    };
  }
  
  default <N> Result<N, E> flatMap(@NonNull Function<V, Result<N, E>> fn) {
    return switch (this) {
      case Ok(var item) -> Remap.returnRemapped(fn.apply(item));
      case Err(var ignored) -> new Err<>(ignored);
    };
  }
  
  default <N> Result<Fuse<V, N>, E> fuse(@NonNull Result<N, E> another) {
    return fuse(another, TakeFrom.HEAD);
  }
  
  default <N> Result<Fuse<V, N>, E> fuse(@NonNull Result<N, E> another, TakeFrom errFrom) {
    return errFrom
      .takeError(this, another)
      .<Result<Fuse<V, N>, E>>map(it -> new Err<>(it))
      .orElseGet(() -> new Ok<>(new Fuse<>(this.value(), another.value())));
  }
  
  default Result<V, E> peek(@NonNull Consumer<V> fn) {
    return switch (this) {
      case Ok(var item) -> {
        fn.accept(item);
        yield this;
      }
      case Err(var ignored) -> this;
    };
  }
  
  default Result<V, E> peek(@NonNull Predicate<V> cond, @NonNull Consumer<V> fn) {
    return switch (this) {
      case Ok(var item) when cond.test(item) -> {
        fn.accept(item);
        yield this;
      }
      default -> this;
    };
  }
  
  default Result<V, E> inspect(@NonNull Consumer<E> fn) {
    return switch (this) {
      case Ok(var ignored) -> this;
      case Err(var item) -> {
        fn.accept(item);
        yield this;
      }
    };
  }
  
  default Result<V, E> inspect(@NonNull Predicate<E> cond, @NonNull Consumer<E> fn) {
    return switch (this) {
      case Err(var item) when cond.test(item) -> {
        fn.accept(item);
        yield this;
      }
      default -> this;
    };
  }
  
  default Result<V, E> inspect(@NonNull Class<? extends Exception> type, @NonNull Consumer<E> fn) {
    return switch (this) {
      case Err(var item) when type.isAssignableFrom(item.getClass()) -> {
        fn.accept(item);
        yield this;
      }
      default -> this;
    };
  }
  
  default Result<V, E> taint(@NonNull E item) {
    if (isOk()) {
      return new Err<>(item);
    } else {
      return this;
    }
  }
  
  default Result<V, E> taint(@NonNull Predicate<V> cond, @NonNull E item) {
    if (isOkAnd(cond)) {
      return new Err<>(item);
    } else {
      return this;
    }
  }
  
  default Result<V, E> recover(@NonNull V item) {
    if (isErr()) {
      return new Ok<>(item);
    } else {
      return this;
    }
  }
  
  default Result<V, E> recover(@NonNull Supplier<V> fn) {
    return recover(fn.get());
  }
  
  default Result<V, E> recover(@NonNull Function<E, V> fn) {
    return switch (this) {
      case Ok(var item) -> this;
      case Err(var item) -> recover(fn.apply(item));
    };
  }
  
  default Result<V, E> recover(@NonNull Predicate<E> cond, @NonNull V item) {
    if (isErrAnd(cond)) {
      return new Ok<>(item);
    } else {
      return this;
    }
  }
  
  default Result<V, E> recover(@NonNull Predicate<E> cond, @NonNull Supplier<V> fn) {
    return recover(cond, fn.get());
  }
  
  default Result<V, E> recover(@NonNull Predicate<E> cond, @NonNull Function<E, V> fn) {
    return switch (this) {
      case Err(var item) when cond.test(item) -> recover(fn);
      default -> this;
    };
  }
  
  default Result<V, E> recover(@NonNull Class<? extends Exception> type, @NonNull V item) {
    if (isErrAnd(type)) {
      return new Ok<>(item);
    } else {
      return this;
    }
  }
  
  default Result<V, E> recover(@NonNull Class<? extends Exception> type, @NonNull Supplier<V> fn) {
    return recover(type, fn.get());
  }
  
  default Result<V, E> recover(@NonNull Class<? extends Exception> type, @NonNull Function<E, V> fn) {
    return switch (this) {
      case Err(var item) when type.isAssignableFrom(item.getClass()) -> recover(fn);
      default -> this;
    };
  }
  
  default V value() {
    return switch (this) {
      case Ok(var item) -> item;
      default -> throw new IllegalStateException("not an OK result");
    };
  }
  
  default E err() {
    return switch (this) {
      case Err(var item) -> item;
      default -> throw new IllegalStateException("not an ERR result");
    };
  }
  
  default void ifOk(@NonNull Consumer<V> fn) {
    if (isOk()) {
      fn.accept(value());
    }
  }
  
  default void ifErr(@NonNull Consumer<E> fn) {
    if (isErr()) {
      fn.accept(err());
    }
  }
  
  default V unwrap() {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var item) -> throw new Failure(item);
    };
  }
  
  default V unwrapOr(@NonNull V repl) {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var ignored) -> repl;
    };
  }
  
  default V unwrapOr(@NonNull Supplier<V> fn) {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var ignored) -> Remap.returnSupplied(fn.get());
    };
  }
  
  default V unwrapChecked() throws E {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var item) -> throw item;
    };
  }
  
}