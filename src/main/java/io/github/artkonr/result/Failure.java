package io.github.artkonr.result;

/**
 * A simple {@link RuntimeException}, wrapping checked
 *  exceptions thrown from within implementations of
 *  {@link BaseResult}.
 */
public class Failure extends RuntimeException {

    /**
     * Default constructor.
     * @param cause wrapped exception
     */
    public Failure(Throwable cause) {
        super(cause);
    }
}
