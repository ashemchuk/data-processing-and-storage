package ru.ashemchuk;

public interface SortableList<T> extends Iterable<T> {
        void addFirst(T value);
        void sortPass();
    }