package roj.concurrent;

import roj.reflect.Unaligned;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2025/3/13 8:23
 */
public abstract class ReuseFIFOQueue<T extends ReuseFIFOQueue.Node> {
	public static class Node {volatile Node next;}

	protected static final long
			HEAD_OFFSET = Unaligned.fieldOffset(ReuseFIFOQueue.class, "head"),
			TAIL_OFFSET = Unaligned.fieldOffset(ReuseFIFOQueue.class, "tail"),
			RECYCLE_OFFSET = Unaligned.fieldOffset(ReuseFIFOQueue.class, "recycle");

	protected volatile Node head, tail, recycle;

	public ReuseFIFOQueue() {head = tail = new Node();}

	protected final void addLast(T node) {
		var tail = (Node)U.getAndSetObject(this, TAIL_OFFSET, node);
		tail.next = node;
	}

	protected abstract void recycle(Node node);

	@SuppressWarnings("unchecked")
	protected final T peek() {return (T) head.next;}
	protected final T removeFirst() {return removeIf(null);}
	@SuppressWarnings("unchecked")
	protected final T removeIf(T ifEqual) {
		while (true) {
			var oldHead = head;
			var oldHeadNext = oldHead.next;
			if (oldHeadNext == null || (ifEqual != null && ifEqual != oldHeadNext)) return null;

			if (U.compareAndSwapObject(this, HEAD_OFFSET, oldHead, oldHeadNext)) {
				oldHead.next = null;
				recycle(oldHead);
				return (T) oldHeadNext;
			}
		}
	}
}
