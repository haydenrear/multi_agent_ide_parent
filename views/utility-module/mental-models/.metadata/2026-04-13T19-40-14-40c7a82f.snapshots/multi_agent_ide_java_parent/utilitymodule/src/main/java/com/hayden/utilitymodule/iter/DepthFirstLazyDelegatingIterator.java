package com.hayden.utilitymodule.iter;

import java.util.*;
import java.util.function.Consumer;

public class DepthFirstLazyDelegatingIterator<T> implements Iterator<T> {

    Iterator<T> curr;
    int childrenIndex = 0;
    final List<? extends Iterator<T>> iterators;
    volatile boolean didIterate = false;

    Queue<Iterator<T>> nextQueues = new ArrayDeque<>();

    public DepthFirstLazyDelegatingIterator(Iterator<T> iterators) {
        this.iterators = Collections.singletonList(iterators);
        this.curr = iterators;
    }

    public DepthFirstLazyDelegatingIterator(List<? extends Iterator<T>> iterators) {
        this.curr = iterators.getFirst();
        this.iterators = iterators;
    }

    private void throwIfAlreadyIterated() {
        if (didIterate)
            throw new RuntimeException("Already iterated!");
    }

    @Override
    public boolean hasNext() {
        throwIfAlreadyIterated();
        if (curr.hasNext()) {
            return true;
        } else if (hasNextChild(childrenIndex)) {
            incrementNext();
            return hasNext();
        } else {
            this.didIterate = true;
            return false;
        }
    }

    private void setNext() {
        this.curr = nextQueues.poll();
        if (this.curr == null)
            throw new RuntimeException();
        while (this.curr != null && !this.curr.hasNext()) {
            this.curr = nextQueues.poll();
        }
    }

    @Override
    public T next() {
        throwIfAlreadyIterated();
        if (curr.hasNext()) {
            return curr.next();
        } else {
            if (hasNextChild(childrenIndex)) {
                incrementNext();
                return next();
            } else {
                this.didIterate = true;
                return null;
            }
        }
    }

    private boolean hasNextChild(int childrenIndex) {
        if (iterators.isEmpty())
            return false;
        return childrenIndex + 1 < iterators.size();
    }

    private void incrementNext() {
        childrenIndex += 1;
        curr = iterators.get(childrenIndex);
    }

    public boolean shortCircuitThisIterator() {
        if (hasNextChild(childrenIndex)) {
            incrementNext();
            return true;
        }

        return false;
    }
}
