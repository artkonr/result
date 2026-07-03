package io.github.artkonr.result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.NonNull;

/**
 * A container for the result of an operation that may succeed (OK) or fail (ERR).
 * <p>Result supports functional error handling with transformations, recovery, and composition,
 * along with imperative operations for checking state and extracting values.
 * <pre>{@code
 * // Wrap a fallible operation
 * Result<String, IOException> result = Result.wrap(() ->
 *   Files.readString(Paths.get("data.txt"))
 * );
 *
 * // Chain transformations
 * String output = result
 *   .map(String::trim)
 *   .map(String::toUpperCase)
 *   .peek(System.out::println)
 *   .recover("DEFAULT")
 *   .value();
 * }</pre>
 * <p>The two sealed subtypes are:
 * <ul>
 *  <li>{@link Ok} - successful completion with a value</li>
 *  <li>{@link Err} - failed completion with an exception</li>
 * </ul>
 * @param <V> type of value held by an OK result
 * @param <E> type of exception held by an ERR result
 */
public sealed interface Result<V, E extends Exception> permits Ok, Err {
  
  /**
   * Wraps a fallible operation that may throw any exception.
   * <pre>{@code
   * Result<String, Exception> result = Result.wrap(() ->
   *   Files.readString(Paths.get("file.txt"))
   * );
   * // Result is Ok("file content") or Err(IOException)
   * }</pre>
   * @param fn fallible operation
   * @return OK with result or ERR with thrown exception
   * @param <V> return type
   * @throws IllegalArgumentException if operation is null or returns null
   */
  static <V> Result<V, Exception> wrap(@NonNull Wrap.Supplier<V> fn) {
    V val;
    try {
        val = fn.get();
    } catch (Exception ex) {
        return new Err<>(ex);
    }

    return new Ok<>(val);
  }
  
  /**
   * Wraps a fallible operation, catching only expected exception types.
   * <p>Throws {@code IllegalStateException} if an unexpected exception type is caught.
   * <pre>{@code
   * Result<Integer, IOException> result = Result.wrap(IOException.class, () ->
   *   Integer.parseInt(Files.readString(Paths.get("number.txt")))
   * );
   * // Result is Ok(42), Err(IOException), or throws IllegalStateException on NumberFormatException
   * }</pre>
   * @param errType expected exception type
   * @param fn fallible operation
   * @return OK with result or ERR with thrown exception of expected type
   * @param <V> return type
   * @param <E> expected error type
   * @throws IllegalArgumentException if any argument is null or operation returns null
   * @throws IllegalStateException if caught exception doesn't match expected type
   */
  static <V, E extends Exception> Result<V, E> wrap(
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
   * Creates a copy of an existing Result.
   * <pre>{@code
   * Result<String, IOException> original = new Ok<>("data");
   * Result<String, IOException> copy = Result.from(original);
   * // copy is Ok("data")
   * }</pre>
   * @param source result to copy
   * @return new result with same state as source
   * @param <V> value type
   * @param <E> error type
   * @throws IllegalArgumentException if source is null
   */
  static <V, E extends Exception> Result<V, E> from(@NonNull Result<V, E> source) {
    return switch (source) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(item);
    };
  }
  
  /**
   * Chains multiple Result-producing operations, short-circuiting on the first error.
   * <pre>{@code
   * Result<List<Integer>, IOException> result = Result.chain(Arrays.asList(
   *   () -> new Ok<>(1),
   *   () -> new Ok<>(2),
   *   () -> new Ok<>(3)
   * ));
   * // Result is Ok([1, 2, 3])
   *
   * Result<List<Integer>, IOException> failed = Result.chain(Arrays.asList(
   *   () -> new Ok<>(1),
   *   () -> new Err<>(new IOException()),
   *   () -> new Ok<>(3)
   * ));
   * // Result is Err(IOException) - short-circuited at second operation
   * }</pre>
   * @param invocations operations to invoke in sequence
   * @return OK with list of all values, or ERR from first failed operation
   * @param <V> value type
   * @param <E> error type
   * @throws IllegalArgumentException if invocations is null
   */
  static <V, E extends Exception> Result<List<V>, E> chain(@NonNull Collection<Supplier<Result<V, E>>> invocations) {
    List<Supplier<Result<V, E>>> filtered = invocations.stream()
      .filter(Objects::nonNull)
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
   * Chains multiple Result-producing operations asynchronously on the specified executor.
   * <p>Each operation is executed in parallel on the given executor. Returns OK with list of all values
   * if all succeed, otherwise ERR from the first failed operation.
   * <pre>{@code
   * Executor executor = Executors.newVirtualThreadPerTaskExecutor();
   * CompletableFuture<Result<List<Integer>, IOException>> future = Result.chainAsync(
   *   Arrays.asList(
   *     () -> new Ok<>(1),
   *     () -> new Ok<>(2),
   *     () -> new Ok<>(3)
   *   ),
   *   executor
   * );
   * Result<List<Integer>, IOException> result = future.join();
   * // Result is Ok([1, 2, 3])
   * }</pre>
   * @param invocations operations to invoke in parallel
   * @param runner executor to run operations on
   * @return OK with list of all values, or ERR from first failed operation, wrapped in a future
   * @param <V> value type
   * @param <E> error type
   * @throws IllegalArgumentException if invocations or runner is null
   */
  static <V, E extends Exception> CompletableFuture<Result<List<V>, E>> chainAsync(@NonNull Collection<Supplier<Result<V, E>>> invocations,
                                                                                   @NonNull Executor runner) {
    List<CompletableFuture<Result<V, E>>> results = invocations.stream()
      .filter(Objects::nonNull)
      .map(task -> CompletableFuture.supplyAsync(task, runner))
      .toList();

    CompletableFuture<Result<List<V>, E>> end = CompletableFuture.completedFuture(new Ok<>(new CopyOnWriteArrayList<>()));
    for (var promise : results) {
      end = end.thenCombine(promise, (acc, curr) -> switch (acc) {
        case Ok(var list) -> switch (curr) {
          case Ok(var item) -> { list.add(item); yield acc; }
          case Err(var err) -> new Err<>(err);
        };
        case Err(var ignored) -> acc;
      });
    }

    return end;
  }

  /**
   * Chains multiple Result-producing operations asynchronously using virtual threads.
   * <p>This is a convenience method that automatically creates a virtual thread executor,
   * making it ideal for simple async chains without executor management. Operations are
   * executed in parallel. Returns OK with list of all values if all succeed, otherwise ERR
   * from the first failed operation.
   * <pre>{@code
   * CompletableFuture<Result<List<Integer>, IOException>> future = Result.chainAsync(
   *   Arrays.asList(
   *     () -> new Ok<>(1),
   *     () -> new Ok<>(2),
   *     () -> new Ok<>(3)
   *   )
   * );
   * Result<List<Integer>, IOException> result = future.join();
   * // Result is Ok([1, 2, 3])
   * }</pre>
   * @param invocations operations to invoke in parallel
   * @return OK with list of all values, or ERR from first failed operation, wrapped in a future
   * @param <V> value type
   * @param <E> error type
   * @throws IllegalArgumentException if invocations is null
   */
  static <V, E extends Exception> CompletableFuture<Result<List<V>, E>> chainAsync(@NonNull Collection<Supplier<Result<V, E>>> invocations) {
    try (var runner = Executors.newVirtualThreadPerTaskExecutor()) {
      return chainAsync(invocations, runner);
    }
  }

  /**
   * Combines multiple Results into a single Result containing a list.
   * <p>Returns OK with all values if all Results are OK, otherwise ERR using the specified rule.
   * <pre>{@code
   * List<Result<Integer, IOException>> results = Arrays.asList(
   *   new Ok<>(1),
   *   new Ok<>(2),
   *   new Ok<>(3)
   * );
   * Result<List<Integer>, IOException> joined = Result.join(results, TakeFrom.HEAD);
   * // Result is Ok([1, 2, 3])
   *
   * List<Result<Integer, IOException>> withError = Arrays.asList(
   *   new Ok<>(1),
   *   new Err<>(new IOException("first")),
   *   new Err<>(new IOException("second"))
   * );
   * Result<List<Integer>, IOException> joined = Result.join(withError, TakeFrom.HEAD);
   * // Result is Err(IOException("first"))
   * }</pre>
   * @param results collection of results to combine
   * @param rule which error to return if multiple errors exist (HEAD=first, TAIL=last)
   * @return OK with list of values, or ERR from selected error
   * @param <V> value type
   * @param <E> error type
   * @throws IllegalArgumentException if any argument is null
   */
  static <V, E extends Exception> Result<List<V>, E> join(@NonNull Collection<Result<V, E>> results,
                                                          @NonNull TakeFrom rule) {
    List<Result<V, E>> nonNull = results.stream()
      .filter(Objects::nonNull)
      .toList();
    List<Result<V, E>> errored = nonNull.stream()
      .filter(Result::isErr)
      .toList();
    if (!errored.isEmpty()) {
        Result<V, E> result = switch (rule) {
            case HEAD -> errored.getFirst();
            case TAIL -> errored.getLast();
        };
        return new Err<>(result.err());
    } else {
        return new Ok<>(nonNull.stream()
          .map(Result::value)
          .toList()
        );
      }
  }
  
  /**
   * Converts Result&lt;Optional&lt;V&gt;, E&gt; to Optional&lt;Result&lt;V, E&gt;&gt;.
   * <pre>{@code
   * Result<Optional<String>, IOException> result = new Ok<>(Optional.of("data"));
   * Optional<Result<String, IOException>> elevated = Result.elevate(result);
   * // elevated is Optional.of(Ok("data"))
   *
   * Result<Optional<String>, IOException> empty = new Ok<>(Optional.empty());
   * Optional<Result<String, IOException>> elevated = Result.elevate(empty);
   * // elevated is Optional.empty()
   *
   * Result<Optional<String>, IOException> err = new Err<>(new IOException());
   * Optional<Result<String, IOException>> elevated = Result.elevate(err);
   * // elevated is Optional.of(Err(IOException))
   * }</pre>
   * @param result result containing an optional value
   * @return optional containing a result
   * @param <V> value type
   * @param <E> error type
   * @throws IllegalArgumentException if result is null
   */
  static <V, E extends Exception> Optional<Result<V, E>> elevate(@NonNull Result<Optional<V>, E> result) {
    return switch (result) {
      case Ok(var item) -> item.map(it -> new Ok<>(it));
      case Err(var item) -> Optional.of(new Err<>(item));
    };
  }
  
  /**
   * Checks if this result is successful (OK).
   * <pre>{@code
   * Result<String, IOException> ok = new Ok<>("success");
   * ok.isOk(); // true
   *
   * Result<String, IOException> err = new Err<>(new IOException());
   * err.isOk(); // false
   * }</pre>
   * @return {@code true} if this is an OK result
   */
  default boolean isOk() {
    return this instanceof Ok;
  }
  
  /**
   * Checks if this result is OK and the value satisfies the predicate.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(42);
   * ok.isOkAnd(v -> v > 0); // true
   * ok.isOkAnd(v -> v < 0); // false
   *
   * Result<Integer, IOException> err = new Err<>(new IOException());
   * err.isOkAnd(v -> v > 0); // false
   * }</pre>
   * @param cond predicate to test the value
   * @return {@code true} if OK and predicate holds
   * @throws IllegalArgumentException if predicate is null
   */
  default boolean isOkAnd(@NonNull Predicate<V> cond) {
    return this instanceof Ok(var item) && cond.test(item);
  }
  
  /**
   * Checks if this result is failed (ERR).
   * <pre>{@code
   * Result<String, IOException> err = new Err<>(new IOException());
   * err.isErr(); // true
   *
   * Result<String, IOException> ok = new Ok<>("success");
   * ok.isErr(); // false
   * }</pre>
   * @return {@code true} if this is an ERR result
   */
  default boolean isErr() {
    return this instanceof Err;
  }
  
  /**
   * Checks if this result is ERR and the error is of the specified type or subtype.
   * <pre>{@code
   * Result<String, IOException> err = new Err<>(new IOException());
   * err.isErrAnd(IOException.class); // true
   * err.isErrAnd(Exception.class); // true (parent type)
   * err.isErrAnd(FileNotFoundException.class); // false
   *
   * Result<String, IOException> ok = new Ok<>("success");
   * ok.isErrAnd(IOException.class); // false
   * }</pre>
   * @param type expected exception type
   * @return {@code true} if ERR and error matches type
   * @throws IllegalArgumentException if type is null
   */
  default boolean isErrAnd(@NonNull Class<? extends Exception> type) {
    return this instanceof Err(var item) && type.isAssignableFrom(item.getClass());
  }
  
  /**
   * Checks if this result is ERR and the error satisfies the predicate.
   * <pre>{@code
   * Result<String, IOException> err = new Err<>(new IOException("disk full"));
   * err.isErrAnd(e -> e.getMessage().contains("disk")); // true
   * err.isErrAnd(e -> e.getMessage().contains("network")); // false
   * }</pre>
   * @param cond predicate to test the error
   * @return {@code true} if ERR and predicate holds
   * @throws IllegalArgumentException if predicate is null
   */
  default boolean isErrAnd(@NonNull Predicate<E> cond) {
    return this instanceof Err(var item) && cond.test(item);
  }
  
  /**
   * Transforms the OK value, or passes through the ERR unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<String, IOException> mapped = ok.map(n -> "Value: " + n);
   * // Result is Ok("Value: 5")
   *
   * Result<Integer, IOException> err = new Err<>(new IOException());
   * Result<String, IOException> mapped = err.map(n -> "Value: " + n);
   * // Result is Err(IOException) - unchanged
   * }</pre>
   * @param fn function to apply to OK value
   * @return new Result with transformed OK value or same ERR
   * @param <N> new value type
   * @throws IllegalArgumentException if function is null or returns null
   */
  default <N> Result<N, E> map(@NonNull Function<V, N> fn) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(fn.apply(item));
      case Err(var ignored) -> new Err<>(ignored);
    };
  }
  
  /**
   * Replaces the OK value, or passes through the ERR unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<String, IOException> swapped = ok.swap("replaced");
   * // Result is Ok("replaced")
   * }</pre>
   * @param item replacement value
   * @return new OK result with replacement value, or same ERR
   * @param <N> new value type
   * @throws IllegalArgumentException if item is null
   */
  default <N> Result<N, E> swap(@NonNull N item) {
    return map(ignored -> item);
  }
  
  /**
   * Replaces the OK value using a supplier, or passes through the ERR unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<String, IOException> swapped = ok.swap(() -> "computed");
   * // Result is Ok("computed")
   * }</pre>
   * @param fn supplier for replacement value
   * @return new OK result with supplied value, or same ERR
   * @param <N> new value type
   * @throws IllegalArgumentException if supplier is null
   */
  default <N> Result<N, E> swap(@NonNull Supplier<N> fn) {
    return swap(Remap.returnSupplied(fn.get()));
  }

  /**
   * Converts this Result to a Done, discarding the value.
   * <p>Useful when you only care about success/failure, not the value.
   * <pre>{@code
   * Result<String, IOException> result = new Ok<>("data");
   * Done<IOException> done = result.drop(); // Success
   *
   * Result<String, IOException> err = new Err<>(new IOException());
   * Done<IOException> done = err.drop(); // Failure(IOException)
   * }</pre>
   * @return Success if this is OK, Failure if this is ERR
   */
  default Done<E> drop() {
    return Done.from(this);
  }

  /**
   * Replaces the ERR error, or passes through the OK unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException());
   * Result<Integer, RuntimeException> stacked = err.stack(new RuntimeException("new"));
   * // Result is Err(RuntimeException)
   *
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<Integer, RuntimeException> stacked = ok.stack(new RuntimeException("new"));
   * // Result is Ok(5) - unchanged
   * }</pre>
   * @param repl replacement error
   * @return new ERR result with replacement error, or same OK
   * @param <N> new error type
   * @throws IllegalArgumentException if error is null
   */
  default <N extends Exception> Result<V, N> stack(@NonNull N repl) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var ignored) -> new Err<>(repl);
    };
  }

  /**
   * Transforms the ERR error using a function, or passes through the OK unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("read failed"));
   * Result<Integer, RuntimeException> stacked = err.stack(ex -> new RuntimeException(ex.getMessage()));
   * // Result is Err(RuntimeException("read failed"))
   * }</pre>
   * @param fn function to transform error
   * @return new ERR with transformed error, or same OK
   * @param <N> new error type
   * @throws IllegalArgumentException if function is null
   */
  default <N extends Exception> Result<V, N> stack(@NonNull Function<E, N> fn) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(fn.apply(item));
    };
  }

  /**
   * Replaces the ERR error using a supplier, or passes through the OK unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException());
   * Result<Integer, RuntimeException> stacked = err.stack(() -> new RuntimeException("fallback"));
   * // Result is Err(RuntimeException("fallback"))
   * }</pre>
   * @param fn supplier for replacement error
   * @return new ERR with supplied error, or same OK
   * @param <N> new error type
   * @throws IllegalArgumentException if supplier is null
   */
  default <N extends Exception> Result<V, N> stack(@NonNull Supplier<N> fn) {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var ignored) -> new Err<>(fn.get());
    };
  }
  
  /**
   * Widens the error type to the base {@link Exception} type.
   * <pre>{@code
   * Result<String, IOException> result = new Ok<>("data");
   * Result<String, Exception> upcasted = result.upcast();
   * }</pre>
   * @return result with error type widened to Exception
   */
  default Result<V, Exception> upcast() {
    return switch (this) {
      case Ok(var item) -> new Ok<>(item);
      case Err(var item) -> new Err<>(item);
    };
  }
  
  /**
   * Chains Result-returning operations, flattening nested Results.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<String, IOException> chained = ok.then(n ->
   *   new Ok<>("number: " + n)
   * );
   * // Result is Ok("number: 5")
   *
   * Result<String, IOException> flatMapped = chained.then(s ->
   *   new Err<>(new IOException("failed"))
   * );
   * // Result is Err(IOException)
   * }</pre>
   * @param fn function returning a Result
   * @return flattened Result
   * @param <N> new value type
   * @throws IllegalArgumentException if function is null
   */
  default <N> Result<N, E> then(@NonNull Function<V, Result<N, E>> fn) {
    return switch (this) {
      case Ok(var item) -> Remap.returnSupplied(fn.apply(item));
      case Err(var ignored) -> new Err<>(ignored);
    };
  }
  
  /**
   * Combines two Results into a Fuse containing both OK values, preferring the first error.
   * <pre>{@code
   * Result<Integer, IOException> a = new Ok<>(5);
   * Result<String, IOException> b = new Ok<>("hello");
   * Result<Fuse<Integer, String>, IOException> fused = a.fuse(b);
   * // Result is Ok(Fuse(5, "hello"))
   *
   * Result<Integer, IOException> c = new Ok<>(5);
   * Result<String, IOException> d = new Err<>(new IOException());
   * Result<Fuse<Integer, String>, IOException> fused = c.fuse(d);
   * // Result is Err(IOException) - from d
   * }</pre>
   * @param another second result to combine
   * @return OK with both values in a Fuse, or first error
   * @param <N> second value type
   * @throws IllegalArgumentException if other result is null
   */
  default <N> Result<Fuse<V, N>, E> fuse(@NonNull Result<N, E> another) {
    return fuse(another, TakeFrom.HEAD);
  }

  /**
   * Combines two Results into a Fuse containing both OK values.
   * <pre>{@code
   * Result<Integer, IOException> a = new Ok<>(5);
   * Result<String, IOException> b = new Err<>(new IOException("err1"));
   * Result<String, IOException> c = new Err<>(new IOException("err2"));
   * Result<Fuse<Integer, String>, IOException> fused = a.fuse(b, TakeFrom.TAIL);
   * // Result is Err(IOException) - from b
   *
   * Result<Fuse<Integer, String>, IOException> fused = b.fuse(c, TakeFrom.TAIL);
   * // Result is Err(IOException) - from c (TAIL)
   * }</pre>
   * @param another second result to combine
   * @param errFrom which error to prefer (HEAD=first, TAIL=second)
   * @return OK with both values in a Fuse, or selected error
   * @param <N> second value type
   * @throws IllegalArgumentException if any argument is null
   */
  default <N> Result<Fuse<V, N>, E> fuse(@NonNull Result<N, E> another, @NonNull TakeFrom errFrom) {
    return errFrom
      .takeError(this, another)
      .<Result<Fuse<V, N>, E>>map(Err::new)
      .orElseGet(() -> new Ok<>(new Fuse<>(this.value(), another.value())));
  }
  
  /**
   * Executes a side-effect on the OK value and returns this result unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(42);
   * ok.peek(System.out::println) // prints 42
   *   .map(x -> x * 2);
   * }</pre>
   * @param fn side-effect to execute
   * @return this result unchanged
   * @throws IllegalArgumentException if function is null
   */
  default Result<V, E> peek(@NonNull Consumer<V> fn) {
    if (this instanceof Ok(var item)) {
      fn.accept(item);
    }

    return this;
  }

  /**
   * Executes a side-effect on the OK value if predicate holds, returns this unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(42);
   * ok.peek(x -> x > 0, System.out::println) // prints 42
   *   .peek(x -> x < 0, System.out::println); // skipped
   * }</pre>
   * @param cond predicate to test the value
   * @param fn side-effect to execute
   * @return this result unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> peek(@NonNull Predicate<V> cond, @NonNull Consumer<V> fn) {
    if (this instanceof Ok(var item) && cond.test(item)) {
      fn.accept(item);
    }

    return this;
  }
  
  /**
   * Executes a side-effect on the ERR error and returns this result unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("disk full"));
   * err.inspect(e -> System.err.println("Error: " + e.getMessage()));
   * }</pre>
   * @param fn side-effect to execute
   * @return this result unchanged
   * @throws IllegalArgumentException if function is null
   */
  default Result<V, E> inspect(@NonNull Consumer<E> fn) {
    if (this instanceof Err(var item)) {
      fn.accept(item);
    }
    return this;
  }

  /**
   * Executes a side-effect on the ERR error if predicate holds, returns this unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("timeout"));
   * err.inspect(e -> e.getMessage().contains("timeout"), System.err::println);
   * }</pre>
   * @param cond predicate to test the error
   * @param fn side-effect to execute
   * @return this result unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> inspect(@NonNull Predicate<E> cond, @NonNull Consumer<E> fn) {
    if (this instanceof Err(var item) && cond.test(item)) {
      fn.accept(item);
    }

    return this;
  }

  /**
   * Executes a side-effect on the ERR error if it matches the type, returns this unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new FileNotFoundException("not found"));
   * err.inspect(FileNotFoundException.class, e -> log.warn("File missing: " + e.getMessage()));
   * }</pre>
   * @param type exception type to match
   * @param fn side-effect to execute
   * @return this result unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> inspect(@NonNull Class<? extends Exception> type, @NonNull Consumer<E> fn) {
    if (this instanceof Err(var item) && type.isAssignableFrom(item.getClass())) {
      fn.accept(item);
    }

    return this;
  }
  
  /**
   * Converts an OK result to ERR with the given error, or passes through ERR unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<Integer, IOException> tainted = ok.taint(new IOException("validation failed"));
   * // Result is Err(IOException("validation failed"))
   * }</pre>
   * @param item error to inject
   * @return new ERR with given error, or same ERR
   * @throws IllegalArgumentException if error is null
   */
  default Result<V, E> taint(@NonNull E item) {
    return switch (this) {
      case Ok(var ignored) -> new Err<>(item);
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an OK result to ERR if predicate holds, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(-5);
   * Result<Integer, IOException> tainted = ok.taint(x -> x < 0, new IOException("negative"));
   * // Result is Err(IOException("negative"))
   * }</pre>
   * @param cond predicate to test the value
   * @param item error to inject
   * @return new ERR if OK and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> taint(@NonNull Predicate<V> cond, @NonNull E item) {
    return switch (this) {
      case Ok(var val) when cond.test(val) -> new Err<>(item);
      case Ok(var val) -> new Ok<>(val);
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an OK result to ERR using a supplier, or passes through ERR unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<Integer, IOException> tainted = ok.taint(() -> new IOException("tainted"));
   * // Result is Err(IOException("tainted"))
   * }</pre>
   * @param fn supplier for error to inject
   * @return new ERR with supplied error, or same ERR
   * @throws IllegalArgumentException if supplier is null
   */
  default Result<V, E> taint(@NonNull Supplier<E> fn) {
    return switch (this) {
      case Ok(var ignored) -> new Err<>(fn.get());
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an OK result to ERR by transforming the value, or passes through ERR unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<Integer, IOException> tainted = ok.taint(v -> new IOException("value: " + v));
   * // Result is Err(IOException("value: 5"))
   * }</pre>
   * @param fn function to transform value to error
   * @return new ERR with transformed error, or same ERR
   * @throws IllegalArgumentException if function is null
   */
  default Result<V, E> taint(@NonNull Function<V, E> fn) {
    return switch (this) {
      case Ok(var item) -> new Err<>(fn.apply(item));
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an OK result to ERR if predicate holds using a supplier, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<Integer, IOException> tainted = ok.taint(v -> v > 0, () -> new IOException("positive"));
   * // Result is Err(IOException("positive"))
   * }</pre>
   * @param cond predicate to test the value
   * @param fn supplier for error to inject
   * @return new ERR if OK and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> taint(@NonNull Predicate<V> cond, @NonNull Supplier<E> fn) {
    return switch (this) {
      case Ok(var item) when cond.test(item) -> new Err<>(fn.get());
      case Ok(var item) -> new Ok<>(item);
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an OK result to ERR if predicate holds by transforming the value, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(5);
   * Result<Integer, IOException> tainted = ok.taint(v -> v > 0, v -> new IOException("too high: " + v));
   * // Result is Err(IOException("too high: 5"))
   * }</pre>
   * @param cond predicate to test the value
   * @param fn function to transform value to error
   * @return new ERR if OK and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> taint(@NonNull Predicate<V> cond, @NonNull Function<V, E> fn) {
    return switch (this) {
      case Ok(var item) when cond.test(item) -> new Err<>(fn.apply(item));
      case Ok(var item) -> new Ok<>(item);
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an ERR result to OK with the given value, or passes through OK unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException());
   * Result<Integer, IOException> recovered = err.recover(0);
   * // Result is Ok(0)
   * }</pre>
   * @param item replacement value
   * @return new OK with given value, or same OK
   * @throws IllegalArgumentException if item is null
   */
  default Result<V, E> recover(@NonNull V item) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var ignored) -> new Ok<>(item);
    };
  }

  /**
   * Converts an ERR result to OK using a supplier, or passes through OK unchanged.
   * <pre>{@code
   * Result<List<String>, IOException> err = new Err<>(new IOException());
   * Result<List<String>, IOException> recovered = err.recover(() -> Collections.emptyList());
   * // Result is Ok([])
   * }</pre>
   * @param fn supplier for replacement value
   * @return new OK with supplied value, or same OK
   * @throws IllegalArgumentException if supplier is null
   */
  default Result<V, E> recover(@NonNull Supplier<V> fn) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var ignored) -> new Ok<>(fn.get());
    };
  }

  /**
   * Converts an ERR result to OK by transforming the error, or passes through OK unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("number format"));
   * Result<Integer, IOException> recovered = err.recover(e -> 42);
   * // Result is Ok(42)
   * }</pre>
   * @param fn function to transform error to value
   * @return new OK with transformed value, or same OK
   * @throws IllegalArgumentException if function is null
   */
  default Result<V, E> recover(@NonNull Function<E, V> fn) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) -> new Ok<>(fn.apply(err));
    };
  }

  /**
   * Converts an ERR matching a predicate to OK with given value, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("timeout"));
   * Result<Integer, IOException> recovered = err.recover(
   *   e -> e.getMessage().contains("timeout"),
   *   0
   * );
   * // Result is Ok(0)
   * }</pre>
   * @param cond predicate to test the error
   * @param item replacement value
   * @return new OK if ERR and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> recover(@NonNull Predicate<E> cond, @NonNull V item) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) when cond.test(err) -> new Ok<>(item);
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an ERR matching a predicate to OK using a supplier, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("timeout"));
   * Result<Integer, IOException> recovered = err.recover(
   *   e -> e.getMessage().contains("timeout"),
   *   () -> 0
   * );
   * // Result is Ok(0)
   * }</pre>
   * @param cond predicate to test the error
   * @param fn supplier for replacement value
   * @return new OK if ERR and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> recover(@NonNull Predicate<E> cond, @NonNull Supplier<V> fn) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) when cond.test(err) -> new Ok<>(fn.get());
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an ERR matching a predicate to OK by transforming error, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new IOException("oops"));
   * Result<Integer, IOException> recovered = err.recover(
   *   e -> e.getMessage().contains("oops"),
   *   e -> 99
   * );
   * // Result is Ok(99)
   * }</pre>
   * @param cond predicate to test the error
   * @param fn function to transform error to value
   * @return new OK if ERR and predicate holds, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> recover(@NonNull Predicate<E> cond, @NonNull Function<E, V> fn) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) when cond.test(err) -> new Ok<>(fn.apply(err));
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an ERR of a specific type to OK with given value, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new FileNotFoundException());
   * Result<Integer, IOException> recovered = err.recover(FileNotFoundException.class, 0);
   * // Result is Ok(0)
   *
   * Result<Integer, IOException> other = new Err<>(new IOException());
   * Result<Integer, IOException> unchanged = other.recover(FileNotFoundException.class, 0);
   * // Result is Err(IOException) - unchanged
   * }</pre>
   * @param type exception type to match
   * @param item replacement value
   * @return new OK if ERR matches type, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> recover(@NonNull Class<? extends Exception> type, @NonNull V item) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) when type.isAssignableFrom(err.getClass()) -> new Ok<>(item);
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an ERR of a specific type to OK using a supplier, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new FileNotFoundException());
   * Result<Integer, IOException> recovered = err.recover(
   *   FileNotFoundException.class,
   *   () -> 0
   * );
   * // Result is Ok(0)
   * }</pre>
   * @param type exception type to match
   * @param fn supplier for replacement value
   * @return new OK if ERR matches type, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> recover(@NonNull Class<? extends Exception> type, @NonNull Supplier<V> fn) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) when type.isAssignableFrom(err.getClass()) -> new Ok<>(fn.get());
      case Err(var retained) -> new Err<>(retained);
    };
  }

  /**
   * Converts an ERR of a specific type to OK by transforming error, or passes through unchanged.
   * <pre>{@code
   * Result<Integer, IOException> err = new Err<>(new FileNotFoundException("missing.txt"));
   * Result<Integer, IOException> recovered = err.recover(
   *   FileNotFoundException.class,
   *   e -> 0
   * );
   * // Result is Ok(0)
   * }</pre>
   * @param type exception type to match
   * @param fn function to transform error to value
   * @return new OK if ERR matches type, otherwise unchanged
   * @throws IllegalArgumentException if any argument is null
   */
  default Result<V, E> recover(@NonNull Class<? extends Exception> type, @NonNull Function<E, V> fn) {
    return switch (this) {
      case Ok(var val) -> new Ok<>(val);
      case Err(var err) when type.isAssignableFrom(err.getClass()) -> new Ok<>(fn.apply(err));
      case Err(var retained) -> new Err<>(retained);
    };
  }
  
  /**
   * Extracts the value from an OK result.
   * <pre>{@code
   * Result<String, IOException> ok = new Ok<>("data");
   * String value = ok.value(); // "data"
   * }</pre>
   * @return the OK value
   * @throws IllegalStateException if this is an ERR result
   */
  default V value() {
    if (this instanceof Ok(var item)) {
      return item;
    }

    throw new IllegalStateException("not an OK result");
  }

  /**
   * Extracts the error from an ERR result.
   * <pre>{@code
   * Result<String, IOException> err = new Err<>(new IOException("read failed"));
   * IOException error = err.err(); // IOException
   * }</pre>
   * @return the ERR error
   * @throws IllegalStateException if this is an OK result
   */
  default E err() {
    if (this instanceof Err(var item)) {
      return item;
    }

    throw new IllegalStateException("not an ERR result");
  }

  /**
   * Executes an action if this is OK, does nothing if ERR.
   * <pre>{@code
   * Result<String, IOException> ok = new Ok<>("data");
   * ok.ifOk(data -> System.out.println("Got: " + data)); // prints
   *
   * Result<String, IOException> err = new Err<>(new IOException());
   * err.ifOk(data -> System.out.println("Got: " + data)); // no-op
   * }</pre>
   * @param fn action to execute with OK value
   * @throws IllegalArgumentException if function is null
   */
  default void ifOk(@NonNull Consumer<V> fn) {
    if (isOk()) {
      fn.accept(value());
    }
  }

  /**
   * Executes an action if this is ERR, does nothing if OK.
   * <pre>{@code
   * Result<String, IOException> err = new Err<>(new IOException());
   * err.ifErr(e -> System.err.println("Error: " + e.getMessage())); // prints
   *
   * Result<String, IOException> ok = new Ok<>("data");
   * ok.ifErr(e -> System.err.println("Error: " + e.getMessage())); // no-op
   * }</pre>
   * @param fn action to execute with ERR error
   * @throws IllegalArgumentException if function is null
   */
  default void ifErr(@NonNull Consumer<E> fn) {
    if (isErr()) {
      fn.accept(err());
    }
  }
  
  /**
   * Extracts the OK value, or throws a {@link Wrap.Failure} wrapping the error.
   * <pre>{@code
   * Result<String, IOException> ok = new Ok<>("data");
   * String data = ok.unwrap(); // "data"
   *
   * Result<String, IOException> err = new Err<>(new IOException());
   * String data = err.unwrap(); // throws Failure(IOException)
   * }</pre>
   * @return the OK value
   * @throws Wrap.Failure wrapping the ERR error
   */
  default V unwrap() {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var item) -> throw new Wrap.Failure(item);
    };
  }

  /**
   * Extracts the OK value, or returns a default value if ERR.
   * <pre>{@code
   * Result<Integer, IOException> ok = new Ok<>(42);
   * int value = ok.unwrapOr(0); // 42
   *
   * Result<Integer, IOException> err = new Err<>(new IOException());
   * int value = err.unwrapOr(0); // 0
   * }</pre>
   * @param repl default value if ERR
   * @return OK value or replacement
   * @throws IllegalArgumentException if replacement is null
   */
  default V unwrapOr(@NonNull V repl) {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var ignored) -> repl;
    };
  }

  /**
   * Extracts the OK value, or supplies a default value if ERR.
   * <pre>{@code
   * Result<List<String>, IOException> ok = new Ok<>(Arrays.asList("a", "b"));
   * List<String> list = ok.unwrapOr(Collections::emptyList); // [a, b]
   *
   * Result<List<String>, IOException> err = new Err<>(new IOException());
   * List<String> list = err.unwrapOr(Collections::emptyList); // []
   * }</pre>
   * @param fn supplier for default value if ERR
   * @return OK value or supplied default
   * @throws IllegalArgumentException if supplier is null
   */
  default V unwrapOr(@NonNull Supplier<V> fn) {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var ignored) -> Remap.returnSupplied(fn.get());
    };
  }

  /**
   * Extracts the OK value, or throws the checked error if ERR.
   * <pre>{@code
   * Result<String, IOException> ok = new Ok<>("data");
   * String data = ok.unwrapChecked(); // "data"
   *
   * Result<String, IOException> err = new Err<>(new IOException());
   * String data = err.unwrapChecked(); // throws IOException
   * }</pre>
   * @return the OK value
   * @throws E the ERR error
   */
  default V unwrapChecked() throws E {
    return switch (this) {
      case Ok(var item) -> item;
      case Err(var item) -> throw item;
    };
  }

}