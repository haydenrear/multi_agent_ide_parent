package com.hayden.utilitymodule.iter;

import java.util.*;

public class ZigZagIterator<T extends LazyIterator.HasChildren<T>> implements Iterator<T> {

    private final LazyIterator<T> forwardIterator;
    private final LazyIterator<T> backwardIterator;
    private boolean forwardTurn = true; // Switches between forward and backward iterator

    public ZigZagIterator(List<T> first, List<T> second) {
        this(new LazyIterator<>(first), new LazyIterator<>(second));
    }

    public ZigZagIterator(LazyIterator<T> first, LazyIterator<T> second) {
        this.forwardIterator = first;
        this.backwardIterator = second;
    }

    public ZigZagIterator(List<T> first) {
        this(new LazyIterator<>(first.subList(0, first.size() / 2).reversed()),
                new LazyIterator<>(first.subList(first.size() / 2, first.size())));
    }

    @Override
    public boolean hasNext() {
        if (forwardTurn && forwardIterator.hasNext()) {
            return true;
        } else if (!forwardTurn && backwardIterator.hasNext()) {
            return true;
        } else if (!forwardIterator.hasNext() && !backwardIterator.hasNext()) {
            return false;
        } else {
            forwardTurn = !forwardTurn;
            return hasNext();
        }
    }

    @Override
    public T next() {
        T current;
        if (forwardTurn && forwardIterator.hasNext()) {
            current = forwardIterator.next();
        } else if (!forwardTurn && backwardIterator.hasNext()) {
            current = backwardIterator.next();
        } else if (!forwardIterator.hasNext() && !backwardIterator.hasNext()) {
            return null;
        } else {
            forwardTurn = !forwardTurn;
            return next();
        }


        if (forwardTurn && backwardIterator.hasNext()) {
            forwardTurn = false;
        } else if (!forwardTurn && forwardIterator.hasNext()) {
            forwardTurn = true;
        }

        return current;
    }

    public void reset() {
        forwardIterator.reset();
        backwardIterator.reset();
        this.forwardTurn = true;
    }

}

