package roj.concurrent;

import roj.reflect.ReflectionUtils;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2025/3/13 0013 8:23
 */
public class FIFOQueue<T extends FIFOQueue.Node> {
	public static class Node {volatile Node next;}

	private static final long
			HEAD_OFFSET = ReflectionUtils.fieldOffset(FIFOQueue.class, "head"),
			TAIL_OFFSET = ReflectionUtils.fieldOffset(FIFOQueue.class, "tail"),
			NEXT_OFFSET = ReflectionUtils.fieldOffset(Node.class, "next");

	private static final Node SENTIAL = new Node();
	private volatile Node head, tail;

	public FIFOQueue() {
		head = tail = SENTIAL;
	}

	public void addLast(T newNode) {
		while (true) {
			var oldTail = tail;
			var oldTailNext = oldTail.next;
			if (oldTail == tail) {
				if (oldTailNext == null) {
					if (U.compareAndSwapObject(oldTail, NEXT_OFFSET, null, newNode)) {
						U.compareAndSwapObject(this, TAIL_OFFSET, oldTail, newNode);
						return;
					}
				} else {
					U.compareAndSwapObject(this, TAIL_OFFSET, oldTail, oldTailNext);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public T peek() {return (T) head.next;}

	public T removeFirst() {return removeIf(null);}
	@SuppressWarnings("unchecked")
	public T removeIf(T ifEqual) {
		while (true) {
			var oldHead = head;
			var oldHeadNext = oldHead.next;
			if (oldHead == head) {
				if (oldHeadNext == null) return null;

				if (ifEqual == null || ifEqual == oldHeadNext && U.compareAndSwapObject(this, HEAD_OFFSET, oldHead, oldHeadNext)) {
					oldHeadNext.next = null;
					return (T) oldHeadNext;
				}
			}
		}
	}
}
