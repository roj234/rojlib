package roj.asmx.event;

import roj.collect.SimpleList;
import roj.reflect.ReflectionUtils;
import roj.reflect.Unaligned;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/3/21 0021 13:15
 */
public final class ListenerList {
	final String type;
	final ListenerList parent;

	private volatile int modCount;
	private static final long MODCOUNT_OFFSET = ReflectionUtils.fieldOffset(ListenerList.class, "modCount");

	ListenerList mapNext;

	ListenerList(String type, ListenerList parent) {
		this.parent = parent;
		this.type = type;
		instances = Helpers.cast(new List<?>[Priority.PRIORITIES.length]);
		Arrays.fill(instances, Collections.emptyList());
	}

	private final List<EventListener>[] instances;
	private volatile EventListener[] cooked;

	final EventListener[] cook() {
		var val = cooked;
		if (val != null) return val;

		retry:
		for(;;) {
			int mc = modCount;

			var i = 0;
			for (var instance : instances) i += instance.size();

			val = new EventListener[i];
			i = 0;
			for (int k = 0; k < instances.length; k++) {
				var instance = instances[k];
				if (instance.isEmpty()) continue;
				var myi = (SimpleList<EventListener>) instance;

				var array = myi.getInternalArray();
				int size = myi.size();
				if (size > array.length || size + i > val.length) continue retry;

				for (int j = 0; j < size; j++) {
					var offset = Unaligned.ARRAY_OBJECT_BASE_OFFSET + (long) j * Unaligned.ARRAY_OBJECT_INDEX_SCALE;

					var listener = (EventListener) array[j];
					if (listener instanceof EventListenerImpl impl)
						U.compareAndSwapObject(array, offset, listener, listener = impl.impl());

					val[i++] = listener;
				}
			}

			if (U.getAndAddInt(this, MODCOUNT_OFFSET, 1) == mc) {
				assert i == val.length;
				return cooked = val;
			}
		}
	}

	@SuppressWarnings("unchecked")
	final void add(int priority, EventListener listener) {
		var instance = instances[priority];
		if (instance == Collections.EMPTY_LIST) {
			int offset = Unaligned.ARRAY_OBJECT_BASE_OFFSET + priority * Unaligned.ARRAY_OBJECT_INDEX_SCALE;
			while (true) {
				if (U.compareAndSwapObject(instances, offset, Collections.EMPTY_LIST, instance = new SimpleList<>())) break;
				if ((instance = (List<EventListener>) U.getObjectVolatile(instances, offset)) != Collections.EMPTY_LIST) break;
			}
		}

		synchronized (instance) {instance.add(listener);}
		cooked = null;
		U.getAndAddInt(this, MODCOUNT_OFFSET, 1);
	}

	final boolean remove(int priority, Object ref, String name, String desc) {
		var instance = instances[priority];
		synchronized (instance) {
			for (int i = 0; i < instance.size(); i++) {
				if (instance.get(i).isFor(ref, name, desc)) {
					instance.remove(i);
					cooked = null;
					U.getAndAddInt(this, MODCOUNT_OFFSET, 1);
					return true;
				}
			}
		}
		return false;
	}
}