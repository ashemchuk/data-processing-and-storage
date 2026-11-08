package ru.ashemchuk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class NodeLinkedList<T> implements SortableList<T> {
    private final Node<T> head = new Node<>(null);
    private final Comparator<? super T> comparator;
    private final AtomicLong steps = new AtomicLong();

    public NodeLinkedList(Comparator<? super T> comparator) {
        this.comparator = Objects.requireNonNull(comparator, "comparator");
    }

    @Override
    public long stepCount() {
        return steps.get();
    }

    @Override
    public Iterator<T> iterator() {
        return snapshot().iterator();
    }

    private List<T> snapshot() {
        List<Node<T>> lockedNodes = new ArrayList<>();
        List<T> values = new ArrayList<>();
        Node<T> current = head;
        try {
            // Retain the prefix locks so nodes cannot cross the traversal boundary.
            while (current != null) {
                lockedNodes.add(current);
                current.lock.lock();
                current = current.next;
            }
            // All links are now stable; the sentinel has no user value.
            for (int i = 1; i < lockedNodes.size(); i++) {
                values.add(lockedNodes.get(i).content);
            }
            return values;
        } finally {
            for (int i = lockedNodes.size() - 1; i >= 0; i--) {
                lockedNodes.get(i).lock.unlock();
            }
        }
    }

    private static class Node<T> {
        private Node<T> next;
        private final T content;
        private final ReentrantLock lock = new ReentrantLock();

        private Node(T content) {
            this.content = content;
        }
    }

    @Override
    public void sortPass(long betweenStepsMillis, long insideStepMillis)
            throws InterruptedException {
        if (betweenStepsMillis < 0 || insideStepMillis < 0) {
            throw new IllegalArgumentException("Delays must be non-negative");
        }
        Node<T> previous = head;
        while (previous != null) {
            previous = sortStep(previous, insideStepMillis);
            if (previous != null) {
                Thread.sleep(betweenStepsMillis);
            }
        }
    }

    // The saved predecessor may have moved. Find it again in the current order.
    private Node<T> sortStep(Node<T> target, long insideStepMillis)
            throws InterruptedException {
        Node<T> previous = head;
        Node<T> first = null;
        Node<T> second = null;
        previous.lock.lockInterruptibly();
        try {
            while (previous != target) {
                Node<T> next = previous.next;
                if (next == null) {
                    return null;
                }
                next.lock.lockInterruptibly();
                previous.lock.unlock();
                previous = next;
            }
            Node<T> candidate = previous.next;
            if (candidate == null) {
                return null;
            }
            candidate.lock.lockInterruptibly();
            first = candidate;
            candidate = first.next;
            if (candidate == null) {
                return null;
            }
            candidate.lock.lockInterruptibly();
            second = candidate;

            int comparison = comparator.compare(first.content, second.content);
            Thread.sleep(insideStepMillis);
            if (comparison > 0) {
                swapAdjacent(previous);
                steps.incrementAndGet();
                return second;
            }
            steps.incrementAndGet();
            return first;
        } finally {
            if (second != null) {
                second.lock.unlock();
            }
            if (first != null) {
                first.lock.unlock();
            }
            previous.lock.unlock();
        }
    }

    // Caller must hold the locks of previous and its next two nodes.
    // P -> A -> B -> N
    // P -> B -> A -> N
    private void swapAdjacent(Node<T> previous) {
        Node<T> first = previous.next;
        Node<T> second = first.next;
        first.next = second.next;
        second.next = first;
        previous.next = second;
    }

    @Override
    public void addFirst(T content) {
        this.addFirst(new Node<>(content));
    }

    private void addFirst(Node<T> first) {
        head.lock.lock();
        try {
            first.next = head.next;
            head.next = first;
        } finally {
            head.lock.unlock();
        }
    }

}
