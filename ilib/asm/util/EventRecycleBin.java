package ilib.asm.util;

import ilib.util.Reflection;
import roj.collect.MyHashMap;
import roj.concurrent.FastThreadLocal;
import roj.util.Helpers;

import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/22 21:53
 */
public class EventRecycleBin extends MyHashMap<Class<? extends Event>, List<Event>> {
	private static final FastThreadLocal<EventRecycleBin> registry = FastThreadLocal.withInitial(EventRecycleBin::new);

	public static EventRecycleBin getLocalInstance() {
		return registry.get();
	}

	public void recycle(Event e) {
		List<Event> ent = computeIfAbsent(e.getClass(), Helpers.fnArrayList());
		if (ent.size() < 32) ent.add(e);
	}

	@SuppressWarnings("unchecked")
	public <T extends Event> T take(Class<T> type) {
		List<Event> ent = get(type);
		if (ent == null || ent.isEmpty()) return null;
		return (T) fuck(ent.remove(ent.size() - 1));
	}

	private static Event fuck(Event e) {
		Reflection.HELPER.setEventPhase(e, null);
		Reflection.HELPER.resetEvent(e);
		if (e.isCancelable()) e.setCanceled(false);
		e.setResult(Event.Result.DEFAULT);
		return e;
	}
}
