package io.github.artkonr.result;

import lombok.NonNull;

/**
 * A pair of two values, typically used to combine results from two operations.
 * <pre>{@code
 * Result<Integer, IOException> a = new Ok<>(5);
 * Result<String, IOException> b = new Ok<>("hello");
 *
 * Result<Fuse<Integer, String>, IOException> combined = a.fuse(b);
 * // combined is Ok(Fuse(5, "hello"))
 *
 * Fuse<Integer, String> pair = combined.value();
 * int left = pair.left();  // 5
 * String right = pair.right(); // "hello"
 * }</pre>
 * @param left left object
 * @param right right object
 * @param <L> left value type
 * @param <R> right value type
 */
public record Fuse<L, R>(@NonNull L left,
                         @NonNull R right) { }