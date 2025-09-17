package roj.event;

import roj.ci.annotation.IndirectReference;
import roj.collect.ArrayList;
import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;
import roj.util.Helpers;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/3/21 13:15
 */
@FastVarHandle
final class ListenerList {
	final String type;
	final ListenerList parent;

	private volatile int modCount;
	private static final VarHandle MODCOUNT = Handles.lookup().findVarHandle(ListenerList.class, "modCount", int.class);

	@IndirectReference
	ListenerList _next;

	ListenerList(String type, ListenerList parent) {
		this.parent = parent;
		this.type = type;
		instances = Helpers.cast(new List<?>[Priority.PRIORITIES.length]);
		Arrays.fill(instances, Collections.emptyList());
	}

	private final List<EventListener>[] instances;
	private static final VarHandle INSTANCES$L$ARRAY = MethodHandles.arrayElementVarHandle(Object[].class);

	private volatile EventListener[] listeners;

	final boolean isEmpty() {return getListeners().length == 0;}
	final EventListener[] getListeners() {
		var val = listeners;
		if (val != null) return val;

		retry:
		for(;;) {
			int mc = modCount;

			var count = 0;
			for (var instance : instances) count += instance.size();

			val = new EventListener[count];
			count = 0;
			for (List<EventListener> instance : instances) {
				if (instance.isEmpty()) continue;
				var myi = (ArrayList<EventListener>) instance;

				var array = myi.getInternalArray();
				int size = myi.size();
				if (size > array.length || size + count > val.length) continue retry;

				for (int j = 0; j < size; j++) {
					var listener = (EventListener) array[j];
					if (listener instanceof HandlerInvoker impl)
						INSTANCES$L$ARRAY.compareAndSet(array, j, listener, listener = impl.impl());

					val[count++] = listener;
				}
			}

			// Check if sizes still match and no modification occurred
			if (count == val.length && MODCOUNT.compareAndSet(this, mc, mc+1)) {
				listeners = val;
				return val;
			}

			val = listeners;
			if (val != null) return val;
		}
	}

	@SuppressWarnings("unchecked")
	final void add(int priority, EventListener listener) {
		var instance = instances[priority];
		if (instance == Collections.EMPTY_LIST) {
			while (true) {
				if (INSTANCES$L$ARRAY.compareAndSet(instances, priority, Collections.EMPTY_LIST, instance = new ArrayList<>())) break;
				if ((instance = (List<EventListener>) INSTANCES$L$ARRAY.getVolatile(instances, priority)) != Collections.EMPTY_LIST) break;
			}
		}

		synchronized (instance) {instance.add(listener);}
		listeners = null;
		MODCOUNT.getAndAdd(this, 1);
	}

	final boolean remove(int priority, Object ref, String name, String desc) {
		var instance = instances[priority];
		synchronized (instance) {
			for (int i = 0; i < instance.size(); i++) {
				if (instance.get(i).isFor(ref, name, desc)) {
					instance.remove(i);
					listeners = null;
					MODCOUNT.getAndAdd(this, 1);
					return true;
				}
			}
		}
		return false;
	}
}