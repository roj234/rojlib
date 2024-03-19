package roj.asmx.event;

import roj.collect.SimpleList;
import roj.util.Helpers;

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
		instances = Helpers.cast(new SimpleList<?>[Priority.PRIORITIES.length]);
		for (int i = 0; i < instances.length; i++) instances[i] = new SimpleList<>();
	}

	final SimpleList<EventListener>[] instances;
	volatile EventListener[] cooked;

	final EventListener[] cook() {
		if (cooked == null) {
			synchronized (this) {
				SimpleList<EventListener> list = new SimpleList<>();
				for (SimpleList<EventListener> instance : instances) {
					for (int i = 0; i < instance.size(); i++) {
						EventListener lst = instance.get(i);
						if (lst instanceof EventListenerImpl impl)
							instance.set(i, lst = impl.impl());

						list.add(lst);
					}
				}

				cooked = list.toArray(new EventListener[list.size()]);
				// 不double check, 保留最新的结果(大概？)
			}
		}
		return cooked;
	}

	final synchronized void add(int priority, EventListener listener) {
		instances[priority].add(listener);
		cooked = null;
	}

	final synchronized boolean remove(int priority, Object ref, String name, String desc) {
		SimpleList<EventListener> instance = instances[priority];
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