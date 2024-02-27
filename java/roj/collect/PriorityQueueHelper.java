package roj.collect;

import roj.reflect.Bypass;

import java.util.PriorityQueue;

/**
 * @author Roj234
 * @since 2024/2/7 0007 9:44
 */
public interface PriorityQueueHelper {
	PriorityQueueHelper INSTANCE = Bypass.builder(PriorityQueueHelper.class).inline().access(PriorityQueue.class, "queue", "getInternalArray", null).build();

	Object[] getInternalArray(PriorityQueue<?> queue);
}