package com.hayden.utilitymodule.iter;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class BreadthFirstLazyDelegatingIterator<T> implements Iterator<T> {

    @Nullable
    Iterator<T> next;
    final List<? extends Iterator<T>> iterators;
    volatile boolean didIterate = false;

    Queue<Iterator<T>> nextQueues = new ArrayDeque<>();

    public BreadthFirstLazyDelegatingIterator(Iterator<T> iterators) {
        this.iterators = Collections.singletonList(iterators);
        this.next = iterators;
    }

    public BreadthFirstLazyDelegatingIterator(List<? extends Iterator<T>> iterators) {
        this.next = iterators.getFirst();
        this.iterators = iterators;
    }

    private void throwIfAlreadyIterated() {
        if (didIterate)
            throw new RuntimeException("Already iterated!");
    }

    @Override
    public boolean hasNext() {
        throwIfAlreadyIterated();
        if (next != null && next.hasNext()) {
            return true;
        } else {
            return this.iterators.stream()
                    .anyMatch(Iterator::hasNext);
        }
    }

    private void setNext() {
        this.next = nextQueues.poll();
        if (this.next == null)
            throw new RuntimeException();
        while (this.next != null && !this.next.hasNext()) {
            this.next = nextQueues.poll();
        }
    }

    @Override
    public T next() {
        throwIfAlreadyIterated();
        if (nextQueues.isEmpty()) {
            nextQueues.addAll(this.iterators);
            return next();
        } else {
            setNext();
            if (this.next == null) {
                nextQueues.addAll(this.iterators);
                setNext();
            }
            return returnNext();
        }
    }

    private @Nullable T returnNext() {
        if(this.next != null && this.next.hasNext()) {
            return this.next.next();
        }
        return null;
    }


}
