package cn.kungreat.boot.tsl;

import java.nio.channels.SelectionKey;
import java.util.LinkedList;

public class InitLinkedList extends LinkedList<SelectionKey> {

    @Override
    public synchronized SelectionKey getFirst() {
        return super.getFirst();
    }

    @Override
    public synchronized SelectionKey removeFirst() {
        return super.removeFirst();
    }

    @Override
    public synchronized SelectionKey removeLast() {
        return super.removeLast();
    }

    @Override
    public synchronized void addFirst(SelectionKey selectionKey) {
        super.addFirst(selectionKey);
    }

    @Override
    public synchronized void addLast(SelectionKey selectionKey) {
        super.addLast(selectionKey);
    }

    @Override
    public synchronized SelectionKey peek() {
        return super.peek();
    }

    @Override
    public synchronized SelectionKey poll() {
        return super.poll();
    }

    @Override
    public synchronized SelectionKey peekFirst() {
        return super.peekFirst();
    }

    @Override
    public synchronized SelectionKey peekLast() {
        return super.peekLast();
    }

    @Override
    public synchronized SelectionKey pollFirst() {
        return super.pollFirst();
    }

    @Override
    public synchronized SelectionKey pollLast() {
        return super.pollLast();
    }

    @Override
    public synchronized boolean add(SelectionKey selectionKey) {
        return super.add(selectionKey);
    }
}
