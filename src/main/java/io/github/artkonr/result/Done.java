package io.github.artkonr.result;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.NonNull;

/**
 * A container for the result of an operation that may succeed or fail, without carrying a value.
 * <p>Done supports functional error handling with transformations, recovery, and composition,
 * similar to {@link Result} but for void operations.
 * <pre>{@code
 * // Wrap a fallible operation that produces no value
 * Done<IOException> done = Done.wrap(() -> Files.write(path, data));
 *
 * // Chain transformations
 * done.then(() -> new Success<>())
 *    .recover(IOException.class)
 *    .ifSuccess(() -> System.out.println("Success"));
 * }</pre>
 * <p>The two sealed subtypes are:
 * <ul>
 *  <li>{@link Success} - successful completion</li>
 *  <li>{@link Failure} - failed completion with an exception</li>
 * </ul>
 * @param <E> type of exception held by a Failure
 */
public sealed interface Done<E extends Exception> permits Success, Failure {

  /**
   * Wraps a fallible operation that may throw any exception.
   * <pre>{@code
   * Done<Exception> done = Done.wrap(() -> Thread.sleep(1000));
   * // Done is Success or Failure with exception
   * }</pre>
   * @param fn fallible operation
   * @return Success or Failure with thrown exception
   * @throws IllegalArgumentException if operation is null
   */
  static Done<Exception> wrap(@NonNull Wrap.Runnable fn) {
    try {
      fn.run();
      return new Success<>();
    } catch (Exception ex) {
      return new Failure<>(ex);
    }
  }

  /**
   * Wraps a fallible operation, catching only expected exception types.
   * <p>Throws {@code IllegalStateException} if an unexpected exception type is caught.
   * <pre>{@code
   * Done<IOException> done = Done.wrap(IOException.class, () ->
   *   Files.write(path, data)
   * );
   * // Done is Success, Failure(IOException), or throws IllegalStateException
   * }</pre>
   * @param errType expected exception type
   * @param fn fallible operation
   * @return Success or Failure with thrown exception of expected type
   * @param <E> expected error type
   * @throws IllegalArgumentException if any argument is null
   * @throws IllegalStateException if caught exception doesn't match expected type
   */
  static <E extends Exception> Done<E> wrap(
    @NonNull Class<E> errType,
    @NonNull Wrap.Runnable fn
  ) {
    try {
      fn.run();
      return new Success<>();
    } catch (Exception exception) {
      if (errType.isAssignableFrom(exception.getClass())) {
        @SuppressWarnings("unchecked")
        E cast = (E) exception;
        return new Failure<>(cast);
      } else {
        throw new IllegalStateException(
          "unexpected wrapped exception: expected=%s actual=%s".formatted(
            errType.getName(),
            exception.getClass().getName()
          )
        );
      }
    }
  }

  /**
   * Creates a copy of an existing Done.
   * <pre>{@code
   * Done<IOException> original = new Success<>();
   * Done<IOException> copy = Done.from(original);
   * }</pre>
   * @param source done to copy
   * @return new done with same state as source
   * @param <E> error type
   * @throws IllegalArgumentException if source is null
   */
  static <E extends Exception> Done<E> from(@NonNull Done<E> source) {
    return switch (source) {
      case Success() -> new Success<>();
      case Failure(var item) -> new Failure<>(item);
    };
  }

  /**
   * Converts a Result to a Done, discarding the value.
   * <pre>{@code
   * Result<String, IOException> ok = new Ok<>("data");
   * Done<IOException> done = Done.from(ok); // Success
   *
   * Result<String, IOException> err = new Err<>(new IOException());
   * Done<IOException> done = Done.from(err); // Failure
   * }</pre>
   * @param source result to convert
   * @return Success if OK, Failure if ERR
   * @param <V> value type (discarded)
   * @param <E> error type
   * @throws IllegalArgumentException if source is null
   */
  static <V, E extends Exception> Done<E> from(@NonNull Result<V, E> source) {
    return switch (source) {
      case Ok(var ignored) -> new Success<>();
      case Err(var item) -> new Failure<>(item);
    };
  }

  /**
   * Chains multiple Done-producing operations, short-circuiting on the first error.
   * <pre>{@code
   * Done<IOException> done = Done.chain(Arrays.asList(
   *   () -> new Success<>(),
   *   () -> new Success<>(),
   *   () -> new Failure<>(new IOException())
   * ));
   * // Done is Failure - short-circuited at third operation
   * }</pre>
   * @param invocations operations to invoke in sequence
   * @return Success if all succeed, Failure from first failed operation
   * @param <E> error type
   * @throws IllegalArgumentException if invocations is null
   */
  static <E extends Exception> Done<E> chain(@NonNull Collection<Supplier<Done<E>>> invocations) {
    List<Supplier<Done<E>>> filtered = invocations.stream()
      .filter(Objects::nonNull)
      .toList();
    if (!filtered.isEmpty()) {
      Iterator<Supplier<Done<E>>> iterator = filtered.iterator();
      E err = null;
      while (iterator.hasNext()) {
        Done<E> curr = iterator.next().get();

        if (curr == null) {
          continue;
        }

        if (curr.isFailure()) {
          err = curr.failure();
          break;
        }
      }

      if (err == null) {
        return new Success<>();
      } else {
        return new Failure<>(err);
      }
    } else {
      return new Success<>();
    }
  }

  /**
   * Chains multiple Done-producing operations asynchronously on the specified executor.
   * <p>Unlike the synchronous {@link Done#chain(Collection)} which short-circuits on first failure,
   * this async version runs all tasks in parallel and combines their results. Returns Success if
   * all succeed, otherwise Failure from the first completed failure.
   * <pre>{@code
   * Executor executor = Executors.newVirtualThreadPerTaskExecutor();
   * CompletableFuture<Done<IOException>> future = Done.chainAsync(
   *   Arrays.asList(
   *     () -> new Success<>(),
   *     () -> new Success<>(),
   *     () -> new Success<>()
   *   ),
   *   executor
   * );
   * Done<IOException> result = future.join();
   * // Result is Success
   * }</pre>
   * @param invocations operations to invoke in parallel
   * @param runner executor to run operations on
   * @return Success if all succeed, Failure from first failed operation, wrapped in a future
   * @param <E> error type
   * @throws IllegalArgumentException if invocations or runner is null
   */
  static <E extends Exception> CompletableFuture<Done<E>> chainAsync(@NonNull Collection<Supplier<Done<E>>> invocations,
                                                                     @NonNull Executor runner) {
    return invocations.stream()
      .map(task -> CompletableFuture.supplyAsync(task, runner))
      .reduce(
        CompletableFuture.completedFuture(new Success<>()),
        (acc, curr) -> acc.thenCombine(curr, Done::fuse)
      );
  }

  /**
   * Chains multiple Done-producing operations asynchronously using virtual threads.
   * <p>This is a convenience method that automatically creates a virtual thread executor,
   * making it ideal for simple async chains without executor management. Like {@link #chainAsync(Collection, Executor)},
   * this runs all tasks in parallel. Returns Success if all succeed, otherwise Failure from the first
   * completed failure.
   * <pre>{@code
   * CompletableFuture<Done<IOException>> future = Done.chainAsync(
   *   Arrays.asList(
   *     () -> new Success<>(),
   *     () -> new Success<>(),
   *     () -> new Success<>()
   *   )
   * );
   * Done<IOException> result = future.join();
   * // Result is Success
   * }</pre>
   * @param invocations operations to invoke in parallel
   * @return Success if all succeed, Failure from first failed operation, wrapped in a future
   * @param <E> error type
   * @throws IllegalArgumentException if invocations is null
   */
  static <E extends Exception> CompletableFuture<Done<E>> chainAsync(@NonNull Collection<Supplier<Done<E>>> invocations) {
    try (var runner = Executors.newVirtualThreadPerTaskExecutor()) {
      return chainAsync(invocations, runner);
    }
  }

  /**
   * Combines multiple Done instances into a single Done.
   * <p>Returns Success if all are Success, otherwise Failure using the specified rule.
   * <pre>{@code
   * List<Done<IOException>> dones = Arrays.asList(
   *   new Success<>(),
   *   new Failure<>(new IOException()),
   *   new Failure<>(new IOException())
   * );
   * Done<IOException> joined = Done.join(dones, TakeFrom.HEAD);
   * // Done is Failure from first error
   * }</pre>
   * @param results collection of dones to combine
   * @param rule which error to return if multiple errors exist (HEAD=first, TAIL=last)
   * @return Success if all succeed, or Failure from selected error
   * @param <E> error type
   * @throws IllegalArgumentException if any argument is null
   */
  static <E extends Exception> Done<E> join(@NonNull Collection<Done<E>> results,
                                            @NonNull TakeFrom rule) {
    List<Done<E>> nonNull = results.stream()
      .filter(Objects::nonNull)
      .toList();
    List<Done<E>> errored = nonNull.stream()
      .filter(Done::isFailure)
      .toList();
    if (!errored.isEmpty()) {
      Done<E> result = switch (rule) {
        case HEAD -> errored.getFirst();
        case TAIL -> errored.getLast();
      };
      return new Failure<>(result.failure());
    } else {
      return new Success<>();
    }
  }

  /**
   * Checks if this done is successful (Success).
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * done.isSuccess(); // true
   * }</pre>
   * @return {@code true} if this is a Success
   */
  default boolean isSuccess() {
    return this instanceof Success;
  }

  /**
   * Checks if this done is failed (Failure).
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException());
   * done.isFailure(); // true
   * }</pre>
   * @return {@code true} if this is a Failure
   */
  default boolean isFailure() {
    return this instanceof Failure;
  }

  /**
   * Checks if this done is Failure and the error is of the specified type or subtype.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new FileNotFoundException());
   * done.isErrAnd(IOException.class); // true (parent type)
   * done.isErrAnd(FileNotFoundException.class); // true
   * done.isErrAnd(RuntimeException.class); // false
   * }</pre>
   * @param type expected exception type
   * @return {@code true} if Failure and error matches type
   * @throws IllegalArgumentException if type is null
   */
  default boolean isFailureAnd(@NonNull Class<? extends Exception> type) {
    return this instanceof Failure(var item) && type.isAssignableFrom(item.getClass());
  }

  /**
   * Checks if this done is Failure and the error satisfies the predicate.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException("timeout"));
   * done.isErrAnd(e -> e.getMessage().contains("timeout")); // true
   * }</pre>
   * @param cond predicate to test the error
   * @return {@code true} if Failure and predicate holds
   * @throws IllegalArgumentException if predicate is null
   */
  default boolean isFailureAnd(@NonNull Predicate<E> cond) {
    return this instanceof Failure(var item) && cond.test(item);
  }

  /**
   * Replaces the error, or passes through Success unchanged.
   * <pre>{@code
   * Done<IOException> failure = new Failure<>(new IOException());
   * Done<RuntimeException> stacked = failure.stack(new RuntimeException("new"));
   * }</pre>
   * @param repl replacement error
   * @return Failure with replacement error, or same Success
   * @param <N> new error type
   * @throws IllegalArgumentException if error is null
   */
  default <N extends Exception> Done<N> stack(@NonNull N repl) {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var ignored) -> new Failure<>(repl);
    };
  }

  /**
   * Transforms the error using a function, or passes through Success unchanged.
   * <pre>{@code
   * Done<IOException> failure = new Failure<>(new IOException("failed"));
   * Done<RuntimeException> stacked = failure.stack(ex -> new RuntimeException(ex.getMessage()));
   * }</pre>
   * @param fn function to transform error
   * @return Failure with transformed error, or same Success
   * @param <N> new error type
   * @throws IllegalArgumentException if function is null
   */
  default <N extends Exception> Done<N> stack(@NonNull Function<E, N> fn) {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var item) -> new Failure<>(fn.apply(item));
    };
  }

  /**
   * Replaces the error using a supplier, or passes through Success unchanged.
   * <pre>{@code
   * Done<IOException> failure = new Failure<>(new IOException());
   * Done<RuntimeException> stacked = failure.stack(() -> new RuntimeException("fallback"));
   * }</pre>
   * @param fn supplier for replacement error
   * @return Failure with supplied error, or same Success
   * @param <N> new error type
   * @throws IllegalArgumentException if supplier is null
   */
  default <N extends Exception> Done<N> stack(@NonNull Supplier<N> fn) {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var ignored) -> new Failure<>(fn.get());
    };
  }

  /**
   * Converts Success to a Result with the given value, or Failure to Result with error.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * Result<String, IOException> result = done.populate("value");
   * // Result is Ok("value")
   * }</pre>
   * @param item value for Success case
   * @return Ok with value if Success, Err with error if Failure
   * @param <V> value type
   * @throws IllegalArgumentException if item is null
   */
  default <V> Result<V, E> populate(@NonNull V item) {
    return switch (this) {
      case Success() -> new Ok<>(item);
      case Failure(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts Success to a Result with supplied value, or Failure to Result with error.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * Result<String, IOException> result = done.populate(() -> "supplied");
   * }</pre>
   * @param fn supplier for value in Success case
   * @return Ok with supplied value if Success, Err with error if Failure
   * @param <V> value type
   * @throws IllegalArgumentException if supplier is null
   */
  default <V> Result<V, E> populate(@NonNull Supplier<V> fn) {
    return switch (this) {
      case Success() -> new Ok<>(fn.get());
      case Failure(var ignored) -> new Err<>(ignored);
    };
  }

  /**
   * Widens the error type to the base {@link Exception} type.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * Done<Exception> upcasted = done.upcast();
   * }</pre>
   * @return done with error type widened to Exception
   */
  default Done<Exception> upcast() {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var item) -> new Failure<>(item);
    };
  }

  /**
   * Chains Done-returning operations, flattening nested structures.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * Done<IOException> chained = done.then(() -> new Failure<>(new IOException()));
   * }</pre>
   * @param fn function returning a Done
   * @return result of function if Success, same Failure otherwise
   * @throws IllegalArgumentException if function is null
   */
  default Done<E> then(@NonNull Supplier<Done<E>> fn) {
    return switch (this) {
      case Success() -> fn.get();
      case Failure(var ignored) -> new Failure<>(ignored);
    };
  }

  /**
   * Executes a side effect on Success and returns this done unchanged.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * done.peek(() -> System.out.println("Success"));
   * }</pre>
   * @param fn side effect to execute
   * @return this done unchanged
   * @throws IllegalArgumentException if function is null
   */
  default Done<E> peek(@NonNull Runnable fn) {
    if (this instanceof Success) {
      fn.run();
    }

    return this;
  }

  /**
   * Executes a side effect on the error and returns this done unchanged.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException());
   * done.inspect(e -> System.err.println("Error: " + e.getMessage()));
   * }</pre>
   * @param fn side-effect to execute
   * @return this done unchanged
   * @throws IllegalArgumentException if function is null
   */
  default Done<E> inspect(@NonNull Consumer<E> fn) {
    if (this instanceof Failure(var item)) {
      fn.accept(item);
    }
    return this;
  }

  /**
   * Executes a side effect on the error if predicate holds, returns this unchanged.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException("timeout"));
   * done.inspect(e -> e.getMessage().contains("timeout"), System.err::println);
   * }</pre>
   * @param cond predicate to test the error
   * @param fn side-effect to execute
   * @return this done unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Done<E> inspect(@NonNull Predicate<E> cond, @NonNull Consumer<E> fn) {
    if (this instanceof Failure(var item) && cond.test(item)) {
      fn.accept(item);
    }

    return this;
  }

  /**
   * Executes a side effect on the error if it matches the type, returns this unchanged.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new FileNotFoundException());
   * done.inspect(FileNotFoundException.class, e -> log.warn("File missing"));
   * }</pre>
   * @param type exception type to match
   * @param fn side effect to execute
   * @return this done unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Done<E> inspect(@NonNull Class<? extends Exception> type, @NonNull Consumer<E> fn) {
    if (this instanceof Failure(var item) && type.isAssignableFrom(item.getClass())) {
      fn.accept(item);
    }

    return this;
  }

  /**
   * Converts a Success to Failure with the given error, or passes through Failure unchanged.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * Done<IOException> tainted = done.taint(new IOException("validation failed"));
   * }</pre>
   * @param item error to inject
   * @return new Failure with given error, or same Failure
   * @throws IllegalArgumentException if error is null
   */
  default Done<E> taint(@NonNull E item) {
    return switch (this) {
      case Success() -> new Failure<>(item);
      case Failure(var ignored) -> new Failure<>(ignored);
    };
  }

  /**
   * Combines two Dones into one, preferring errors by the specified rule.
   * <pre>{@code
   * Done<IOException> a = new Success<>();
   * Done<IOException> b = new Failure<>(new IOException());
   * Done<IOException> fused = a.fuse(b, TakeFrom.HEAD);
   * // Result is Failure(IOException) - from b (TAIL)
   * }</pre>
   * @param another second done to combine
   * @param errFrom which error to prefer (HEAD=first, TAIL=second)
   * @return Success if both succeed, or selected Failure
   * @throws IllegalArgumentException if any argument is null
   */
  default Done<E> fuse(@NonNull Done<E> another, @NonNull TakeFrom errFrom) {
    return errFrom
      .takeError(this, another)
      .<Done<E>>map(Failure::new)
      .orElseGet(Success::new);
  }

  /**
   * Combines two Done instances with HEAD priority (prefers first error).
   * <pre>{@code
   * Done<IOException> a = new Failure<>(new IOException("first"));
   * Done<IOException> b = new Failure<>(new IOException("second"));
   * Done<IOException> fused = a.fuse(b); // Failure("first")
   * }</pre>
   * @param another second done to combine
   * @return Success if both succeed, or first Failure
   * @throws IllegalArgumentException if other done is null
   */
  default Done<E> fuse(@NonNull Done<E> another) {
    return fuse(another, TakeFrom.HEAD);
  }

  /**
   * Converts a Failure to Success, or keeps Success unchanged.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException());
   * Done<IOException> recovered = done.recover(); // Success
   * }</pre>
   * @return Success if Failure, same Success otherwise
   */
  default Done<E> recover() {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var ignored) -> new Success<>();
    };
  }

  /**
   * Converts a Failure matching predicate to Success, or keeps unchanged.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException("timeout"));
   * Done<IOException> recovered = done.recover(e -> e.getMessage().contains("timeout"));
   * // Result is Success
   * }</pre>
   * @param cond predicate to test the error
   * @return Success if Failure and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if predicate is null
   */
  default Done<E> recover(@NonNull Predicate<E> cond) {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var item) when cond.test(item) -> new Success<>();
      case Failure(var item) -> new Failure<>(item);
    };
  }

  /**
   * Converts a Failure of specific type to Success, or keeps unchanged.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new FileNotFoundException());
   * Done<IOException> recovered = done.recover(FileNotFoundException.class);
   * }</pre>
   * @param type exception type to match
   * @return Success if Failure matches type, otherwise unchanged
   * @throws IllegalArgumentException if type is null
   */
  default Done<E> recover(@NonNull Class<? extends Exception> type) {
    return switch (this) {
      case Success() -> new Success<>();
      case Failure(var item) when type.isAssignableFrom(item.getClass()) -> new Success<>();
      case Failure(var item) -> new Failure<>(item);
    };
  }

  /**
   * Extracts the error from a Failure.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException("error"));
   * IOException error = done.failure(); // IOException
   * }</pre>
   * @return the error
   * @throws IllegalStateException if this is a Success
   */
  default E failure() {
    if (this instanceof Failure(var item)) {
      return item;
    }

    throw new IllegalStateException("not an FAILURE");
  }

  /**
   * Executes an action if this is Success, does nothing if Failure.
   * <pre>{@code
   * Done<IOException> done = new Success<>();
   * done.ifSuccess(() -> System.out.println("Success"));
   * }</pre>
   * @param fn action to execute
   * @throws IllegalArgumentException if function is null
   */
  default void ifSuccess(@NonNull Runnable fn) {
    if (this instanceof Success) {
      fn.run();
    }
  }

  /**
   * Executes an action if this is Failure, does nothing if Success.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException());
   * done.ifFailure(e -> System.err.println("Error: " + e.getMessage()));
   * }</pre>
   * @param fn action to execute with error
   * @throws IllegalArgumentException if function is null
   */
  default void ifFailure(@NonNull Consumer<E> fn) {
    if (this instanceof Failure(var item)) {
      fn.accept(item);
    }
  }

  /**
   * Throws a {@link Wrap.Failure} wrapping the error if Failure, does nothing if Success.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException());
   * try {
   *   done.unwrap(); // throws Failure(IOException)
   * } catch (Wrap.Failure e) {
   *   Throwable cause = e.getCause();
   * }
   * }</pre>
   * @throws Wrap.Failure wrapping the error if Failure
   */
  default void unwrap() {
    if (this instanceof Failure(var item)) {
      throw new Wrap.Failure(item);
    }
  }

  /**
   * Throws the checked error if Failure, does nothing if Success.
   * <pre>{@code
   * Done<IOException> done = new Failure<>(new IOException());
   * try {
   *   done.unwrapChecked(); // throws IOException
   * } catch (IOException e) {
   *   // handle
   * }
   * }</pre>
   * @throws E the error if Failure
   */
  default void unwrapChecked() throws E {
    if (this instanceof Failure(var item)) {
      throw item;
    }
  }

}
