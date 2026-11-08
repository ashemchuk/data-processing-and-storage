package ru.ashemchuk;

import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

public class NodeLinkedList<T> implements SortableList<T>{
    private final Node<T> head = new Node<>(null);

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> cur = head;
            @Override
            public boolean hasNext() {
                return cur.next != null;
            }

            @Override
            public T next() {
                var next = cur.next;
                cur = cur.next;
                return next.content;
            }
        };
    }

    private static class Node<T> {
        private Node<T> next;
        private final T content;
        private ReentrantLock lock = new ReentrantLock();

        private Node(T content) {
            this.content = content;
        }
    }

    @Override
    public void sortPass() {

    }

    @Override
    public void addFirst(T content) {
        this.addFirst(new Node<>(content));
    }

//    private void swapNodes(Node<T> a, Node<T> b) {
//        Node<T> tmp = a;
//        a.prev.next = b;
//        a.prev = b.prev;
//        a.next.prev = b;
//        a.next = b.next;
//
//        b.prev.next = a
//        b.prev = tmp.prev;
//    }

    private void addFirst(Node<T> first) {
        var second = head.next;
        head.next = first;
        first.next = second;
    }

}
