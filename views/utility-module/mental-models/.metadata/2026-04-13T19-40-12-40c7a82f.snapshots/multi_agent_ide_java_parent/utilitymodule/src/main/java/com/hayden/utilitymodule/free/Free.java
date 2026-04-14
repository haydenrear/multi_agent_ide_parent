package com.hayden.utilitymodule.free;

import com.hayden.utilitymodule.result.error.SingleError;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;


public sealed interface Free<F extends Effect, A> {

    Logger log = LoggerFactory.getLogger(Free.class);

    static <F extends Effect, A> Free<F, A> pure(A a) {
        return new Pure<>(a);
    }

    static <F extends Effect, A> Free<F, A> liftF(F f) {
        return new Suspend<>(f);
    }
    static <F extends Effect, A> Free<F, A> err(String f) {
        return err(f, null) ;
    }

    static <F extends Effect, A> Free<F, A> err(String f, Exception t) {
        return new Error<>(new FreeError(f, t));
    }

    static <F extends Effect, A> A parse(Free<F, A> p, Interpreter<F, A> interpreter) {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return interpreter.mapErr().apply(detectedInterruption());
            }
            switch (p) {
                // Preserve F & A in the pattern:
                case Free.Pure<F, A> v -> {
                    return v.a();
                }

                // Keep F so no cast is needed:
                case Free.Suspend<F, ? extends A> s -> {
                    try {
                        p = interpreter.apply(s.f());

                        if (p instanceof Free.Error<F,A> err) {
                            return interpreter.mapErr().apply(err);
                        }
                    } catch(Exception t) {
                        Error<F, A> err = new Free.Error(t.getMessage(), t);
                        return interpreter.mapErr().apply(err);
                    }
                }

                // Capture the wildcard via a helper so we don’t cast mapper/result:
                case Free.FlatMapped<F, ?, A> fm -> {
                    try {
                        p = stepFlatMapped(fm, interpreter);

                        if (p instanceof Free.Error<F,A> err) {
                            return interpreter.mapErr().apply(err);
                        }
                    } catch(Exception t) {
                        Error<F, A> err = new Free.Error(t.getMessage(), t);
                        return interpreter.mapErr().apply(err);
                    }
                }
                case Free.Error<F, A> v -> {
                    try {
                        return interpreter.mapErr().apply(v);
                    } catch(Exception t) {
                        Error<F, A> err = new Free.Error(t.getMessage(), t);
                        return interpreter.mapErr().apply(err);
                    }
                }
            }
        }
    }

    // Helper to capture X in FlatMapped<F, X, A>
    private static <F extends Effect, X, A> Free<F, A> stepFlatMapped(
            FlatMapped<F, X, A> fm,
            Interpreter<F, A> interpreter
    ) {
        var sub = fm.f();
        return switch (sub) {
            case Free.Pure<F, X> v      -> fm.mapper().apply(v.a());
            case Free.Suspend<F, X> s   -> {
                Function<X, Free<F, A>> mapper = fm.mapper();
                yield interpreter.apply(s.f()).flatMap(mapped -> {
                    try {
                        if (Thread.currentThread().isInterrupted()) {
                            return detectedInterruption();
                        }
                        Free<F, A> m =  mapper.apply((X) mapped);
                        if (m instanceof Error<F, A>(
                                FreeError error
                        )) {
                            return new Error<>(error);
                        }

                        return m;
                    } catch (ClassCastException castException) {
                        log.error("Could not cast {}.", mapped.getClass(), castException);
                        throw castException;
                    } catch (Exception t) {
                        return new Free.Error<>(t.getMessage(), t);
                    }
                });
            }
            case Free.FlatMapped<F, ?, ?> ignored ->
                    throw new IllegalStateException("Nested FlatMapped is not expected here");
            case Free.Error<F, ?> v -> new Error<F, A>(v.error());
        };
    }

    private static <F extends Effect, A> @NotNull Error<F, A> detectedInterruption() {
        return new Error<>("Detected an interruption.", new InterruptedException());
    }

    record Pure<F extends Effect, A>(A a) implements Free<F, A> {
        @Override public <B> Free<F, B> flatMap(Function<A, Free<F, B>> f) { return f.apply(a); }
    }

    record Suspend<F extends Effect, A>(F f) implements Free<F, A> {
        @Override public <B> Free<F, B> flatMap(Function<A, Free<F, B>> g) { return new FlatMapped<>(this, g); }
    }

    record FlatMapped<F extends Effect, A, B>(Free<F, A> f, Function<A, Free<F, B>> mapper)
            implements Free<F, B> {
        @Override
        public <C> Free<F, C> flatMap(Function<B, Free<F, C>> g) {
            return new FlatMapped<>(f, x -> {
                try {
                    return mapper.apply(x).flatMap(g);
                } catch (Exception e) {
                    return Free.err(e.getMessage(), e);
                }
            });
        }
    }

    record FreeError(String getMessage, Exception t) implements SingleError {}

    record Error<F extends Effect, A>(FreeError error) implements Free<F, A> {

        public Error(String err, Exception e) {
            this(new FreeError(err, e));
        }

        @Override
        public <B> Free<F, B> flatMap(Function<A, Free<F, B>> f) {
            return new Error<>(error);
        }
    }

    <B> Free<F, B> flatMap(Function<A, Free<F, B>> f);
}
