package io.github.artkonr.result;

import lombok.NonNull;

public record Fuse<L, R>(@NonNull L left,
                         @NonNull R right) { }