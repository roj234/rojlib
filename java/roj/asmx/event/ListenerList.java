package roj.asmx.event;

import roj.collect.SimpleList;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/3/21 0021 13:15
 */
public final class ListenerList {
	final String type;
	final ListenerList parent;

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
		if (val == null) {
			// 2024-04-22 10:37 可以把EventListener变成单向链表（加一个next字段），然后改成SentryNode[] instances
			// 除了没什么意义，都挺好的

			synchronized (this) {
				var i = 0;
				for (List<EventListener> instance : instances) {
					i += instance.size();
				}

				val = new EventListener[i];
				i = 0;
				for (List<EventListener> instance : instances) {
					for (int j = 0; j < instance.size(); j++) {
						EventListener listener = instance.get(j);
						if (listener instanceof EventListenerImpl impl)
							instance.set(j, listener = impl.impl());

						val[i++] = listener;
					}
				}

				assert i == val.length;
				cooked = val;
			}
		}
		return val;
	}

	final synchronized void add(int priority, EventListener listener) {
		var instance = instances[priority];
		if (instance == Collections.EMPTY_LIST) instances[priority] = instance = new SimpleList<>();

		instance.add(listener);
		cooked = null;
	}

	final synchronized boolean remove(int priority, Object ref, String name, String desc) {
		var instance = instances[priority];
		for (int i = 0; i < instance.size(); i++) {
			if (instance.get(i).isFor(ref, name, desc)) {
				instance.remove(i);
				cooked = null;
				return true;
			}
		}
		return false;
	}
}