# Result

An alternative to `try/catch` following the design pattern offered by `java.util.Optional` and `Result` in Rust.

**License**: Apache License 2.0 - this library is unconditionally open-source.

## Getting started

Library installation is available for [Maven](#maven) and [Gradle](#gradle) and targets Java 17.

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.artkonr</groupId>
        <artifactId>result</artifactId>
        <version>${versions.result}</version>
    </dependency>
</dependencies>
```

### Gradle

```groovy
depencencies {
    implementation 'io.github.artkonr:result:${resultVersion}'
}
```

## Cookbook

### Functional style

The library is designed to offer a fluent functional-style API to interact with errors, very similarly to how the API of `java.util.Optional`:

```java


import java.io.IOException;

public void doingSomeWork() {
    Result<String, RuntimeException> fallibleIO = Result
            .wrap(IOException.class, () -> doSomeIOWork()) // wrap some IO operation
            .map(io -> convertIOResult(io))                // convert item
            .taint(
                    text -> !isTextOk(text),               // check if the value is what we expect
                    text -> createError(text)              // and make and error if it is not
            )
            .mapErr(generic -> wrapError(generic));        // wrap into a domain-specific error

    // we can recover from errors by using fallbacks
    String getOrFallback = fallibleIO.unwrapOr("fallback");

    // or just go fingers-crossed
    String couldHurt = fallibleIO.unwrap();

    // if we don't care about the result value,
    // we can always drop it and, say, invoke a callback
    FlagResult<RuntimeException> dropped = fallibleIO.drop();
    dropped.ifOk(() -> System.out.println("well done"));
}
```

### POJO-style

Of course, it is also possible to work in a more traditional way:

```java


import java.io.File;
import java.io.IOException;

public void doingSomeWork() {
    Result<File, IOException> fallibleIO;
    try {
        File file = doSomeIOWork();
        fallibleIO = Result.ok(file);
    } catch (IOException ex) {
        fallibleIO = Result.err(ex);
    }

    if (fallibleIO.isOk()) {
        File okFile = fallibleIO.get();
        readFileContents(okFile);
    } else {
        IOException err = fallibleIO.getErr();
        logError(err);
    }
}

```

## Building

The library is built with Maven:

```shell
mvn clean package
```
