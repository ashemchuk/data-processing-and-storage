package ru.ashemchuk;

public class Node<T> {
    public Node<T> prev;
    public Node<T> next;
    public final T content;

    Node(T content) {
        this.content = content;
    }
}
