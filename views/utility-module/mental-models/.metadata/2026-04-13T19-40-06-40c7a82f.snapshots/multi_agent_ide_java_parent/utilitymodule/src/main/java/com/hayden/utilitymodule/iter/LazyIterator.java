package com.hayden.utilitymodule.iter;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LazyIterator<T extends LazyIterator.HasChildren<T>> implements Iterator<T> {

    public interface HasChildren<T> {
        Iterator<T> childrenIter();
    }

    private final List<T> receivedChildren;
    private final Deque<T> futureNodes;
    private int currentIndex;

    public LazyIterator(List<T> receivedChildren) {
        this(receivedChildren, -1);
    }

    public LazyIterator(List<T> receivedChildren, int recP) {
        this.receivedChildren = receivedChildren;
        this.futureNodes = new ArrayDeque<>();
        this.currentIndex = recP + 1;
        this.addNextFutureNode();
    }


    @Override
    public boolean hasNext() {
        return !futureNodes.isEmpty() || currentIndex < receivedChildren.size();
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more elements to iterate.");
        }


        if (!futureNodes.isEmpty()) {
            return futureNodes.pop();
        }

        addNextFutureNode();

        return this.futureNodes.pop();
    }

    private @NotNull void addNextFutureNode() {
        if (currentIndex < receivedChildren.size()) {
            T nextNode = receivedChildren.get(currentIndex);
            futureNodes.add(nextNode);
            Iterator<T> fileNodeIterator = nextNode.childrenIter();
            if (fileNodeIterator.hasNext())
                futureNodes.addAll(Lists.newArrayList(fileNodeIterator));
            currentIndex += 1;
        }
    }

    public void reset() {
        currentIndex = 0;
        futureNodes.clear();
    }

}
