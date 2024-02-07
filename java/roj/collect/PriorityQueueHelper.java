package roj.collect;

import roj.reflect.DirectAccessor;

import java.util.PriorityQueue;

/**
 * @author Roj234
 * @since 2024/2/7 0007 9:44
 */
public interface PriorityQueueHelper {
	PriorityQueueHelper INSTANCE = DirectAccessor.builder(PriorityQueueHelper.class).access(PriorityQueue.class, "queue", "getInternalArray", null).build();

	Object[] getInternalArray(PriorityQueue<?> queue);
}