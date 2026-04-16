package io.github.artkonr.result;


import lombok.NonNull;

public record Err<V, E extends Exception>(@NonNull E item) implements Result<V, E> { }