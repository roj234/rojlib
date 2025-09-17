package roj.concurrent;

import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;

import java.lang.invoke.VarHandle;

/**
 * @author Roj234
 * @since 2025/3/13 8:23
 */
@FastVarHandle
public abstract class ReuseFIFOQueue<T extends ReuseFIFOQueue.Node> {
	public static class Node {volatile Node next;}

	private static final VarHandle
			HEAD = Handles.lookup().findVarHandle(ReuseFIFOQueue.class, "head", Node.class),
			TAIL = Handles.lookup().findVarHandle(ReuseFIFOQueue.class, "tail", Node.class);

	protected volatile Node head, tail;

	public ReuseFIFOQueue() {head = tail = new Node();}

	protected final void addLast(T node) {
		var tail = (Node)TAIL.getAndSet(this, node);
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

			if (HEAD.compareAndSet(this, oldHead, oldHeadNext)) {
				oldHead.next = null;
				recycle(oldHead);
				return (T) oldHeadNext;
			}
		}
	}
}
