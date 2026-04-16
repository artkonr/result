package io.github.artkonr.result;

import lombok.NonNull;

public record Ok<V, E extends Exception>(@NonNull V item) implements Result<V, E> { }