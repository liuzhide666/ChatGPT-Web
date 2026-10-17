package org.mozilla.geckoview;

public final class GeckoResult<T> {
    public interface Consumer<U> { void accept(U value); }
    private T value;
    private boolean complete;
    public GeckoResult() {}
    private GeckoResult(T value) { this.value = value; this.complete = true; }
    public static <U> GeckoResult<U> fromValue(U value) { return new GeckoResult<>(value); }
    public void complete(T value) {
        if (complete) throw new IllegalStateException("already complete");
        this.value = value;
        this.complete = true;
    }
    public boolean isComplete() { return complete; }
    public T poll() { return value; }
    public GeckoResult<T> accept(Consumer<T> success, Consumer<Throwable> failure) {
        if (complete) success.accept(value);
        return this;
    }
}
