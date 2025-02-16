package roj.asmx.event;

import roj.ToBeRemoved;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.RawNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Attribute;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.collect.Hasher;
import roj.collect.SimpleList;
import roj.collect.XHashSet;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/3/21 0021 11:50
 */
public class EventBus {
	public static final String SUBSCRIBE_NAME = "roj/asmx/event/Subscribe";

	private static final XHashSet.Shape<String, ListenerList> LM_SHAPE = XHashSet.noCreation(ListenerList.class, "type", "mapNext", Hasher.defaul());
	private static final XHashSet.Shape<String, ListenerInfo.MapEntry> INFO_SHAPE = XHashSet.noCreation(ListenerInfo.MapEntry.class, "owner", "next", Hasher.defaul());

	private final XHashSet<String, ListenerList> eventListenerList = LM_SHAPE.create();
	public ListenerList getListeners(Event event) { return eventListenerList.get(event.getClass().getName()); }
	private ListenerList createListenerList(Class<?> event) {
		ListenerList list;
		if (event != Event.class) {
			Class<?> parent = event.getSuperclass();
			list = eventListenerList.get(parent.getName());
			if (list == null) list = createListenerList(parent);
		} else {
			list = null;
		}
		list = new ListenerList(event.getName(), list);
		eventListenerList.add(list);
		return list;
	}
	private ListenerList createListenerList(Class<?> c, RawNode mn, String event) {
		Class<?> type = null;
		block:
		try {
			type = Class.forName(event);
		} catch (ClassNotFoundException e) {
			for (Method method : c.getMethods()) {
				if (method.getName().equals(mn.name())) {
					Class<?>[] parType = method.getParameterTypes();
					if (TypeHelper.class2asm(parType, method.getReturnType()).equals(mn.rawDesc())) {
						type = parType[0];
						break block;
					}
				}
			}
			Helpers.athrow(e);
		}
		return createListenerList(type);
	}

	public EventBus() {}
	public EventBus(AnnotationRepo repo) {
		List<ListenerInfo> objectList = new SimpleList<>(), staticList = new SimpleList<>();
		for (AnnotatedElement el : repo.annotatedBy(SUBSCRIBE_NAME)) {
			if (entries.get(el.owner()) != null) continue;

			objectList.clear();
			staticList.clear();
			for (AnnotatedElement.Node child : el.parent().children()) {
				Annotation subscribe = child.annotations().get(SUBSCRIBE_NAME);
				if (subscribe != null) {
					var mn = child.node();
					toListenerInfo(mn, subscribe, objectList, staticList);
				}
			}

			entries.add(new ListenerInfo.MapEntry(
				el.owner(),
				objectList.toArray(new ListenerInfo[objectList.size()]),
				staticList.toArray(new ListenerInfo[staticList.size()])
			));
		}
	}
	private final XHashSet<String, ListenerInfo.MapEntry> entries = INFO_SHAPE.create();
	private ListenerInfo[] getListenerInfo(Class<?> type, boolean object) {
		var entry = entries.get(type.getName());
		if (entry == null) {
			var data = Parser.parseConstants(type);
			if (data == null) throw new IllegalArgumentException("无法解析"+type.getName()+"的源文件！");

			List<ListenerInfo> objectList = new SimpleList<>(), staticList = new SimpleList<>();
			for (var mn : data.methods) {
				var annotations = mn.parsedAttr(data.cp, Attribute.ClAnnotations);
				if (annotations == null) continue;
				for (var annotation : annotations.annotations) {
					if (annotation.type().equals(SUBSCRIBE_NAME)) {
						toListenerInfo(mn, annotation, objectList, staticList);
						break;
					}
				}
			}

			if (objectList.isEmpty() & staticList.isEmpty()) {
				throw new IllegalArgumentException(type.getName()+"没有事件监听器");
			}

			entry = new ListenerInfo.MapEntry(
				data.name(),
				objectList.toArray(new ListenerInfo[objectList.size()]),
				staticList.toArray(new ListenerInfo[staticList.size()])
			);
			synchronized (entries) {
				if (!entries.add(entry)) {
					entry = entries.get(type.getName());
				}
			}
		}
		return object ? entry.objectList : entry.staticList;
	}
	private static void toListenerInfo(RawNode mn, Annotation subscribe, List<ListenerInfo> objectList, List<ListenerInfo> staticList) {
		List<Type> types = Type.methodDesc(mn.rawDesc());
		if (types.size() != 2 || types.get(0).isPrimitive() || types.get(1).type != Type.VOID)
			throw new IllegalArgumentException("事件监听函数的参数不合法: 期待[Event, void]而不是"+types);

		int flags = Priority.valueOf(subscribe.getEnumValue("priority", "NORMAL")).ordinal();
		if (subscribe.getBool("receiveCancelled")) flags |= 128;

		ListenerInfo info = new ListenerInfo(types.get(0).owner.replace('/', '.'), mn, (byte) flags);
		((mn.modifier()&Opcodes.ACC_STATIC) != 0 ? staticList : objectList).add(info);
	}

	public void register(Object o) {
		Class<?> c = o instanceof Class<?> ? (Class<?>) o : o.getClass();
		for (var info : getListenerInfo(c, c != o)) {
			var list = eventListenerList.get(info.event);
			if (list == null) {
				synchronized (eventListenerList) {
					if ((list = eventListenerList.get(info.event)) == null) {
						list = createListenerList(c, info.mn, info.event);
					}
				}
			}
			list.add(info.flags&Priority.MASK, new EventListenerImpl(o, info));
		}
	}

	public void unregister(Object o) {
		Class<?> c = o instanceof Class<?> ? (Class<?>) o : o.getClass();
		Object inst = c != o ? o : null;
		for (var info : getListenerInfo(c, c != o)) {
			var list = eventListenerList.get(info.event);
			if (list != null) {
				list.remove(info.flags&Priority.MASK, inst, info.mn.name(), info.mn.rawDesc());
			}
		}
	}

	public boolean hasListener(Class<? extends Event> type) { return eventListenerList.get(type.getName()) != null; }

	public boolean post(Event event) {
		var list = getListeners(event);
		if (list == null) return false;

		EventListener[] listeners;
		do {
			listeners = list.cook();
			for (int i = 0; i < listeners.length; i++) {
				try {
					listeners[i].invoke(event);
				} catch (Throwable e) {
					makeDebugInfo(event, e, list.type, listeners, i);
				}
			}

			list = list.parent;
		} while (list != null);

		return event.isCanceled();
	}

	private static void makeDebugInfo(Event event, Throwable e, String type, EventListener[] listeners, int index) {
		var log = Logger.getLogger();
		log.error("发布 {} 事件 (继承监听器为{}) 时发生了异常:", e, event.getClass().getName(), type);
		log.error("位于第 {}/{} 个接收者(从1开始):", index+1, listeners.length);

		for (int i = 0; i < listeners.length; i++) {
			log.error("  {}: {}", i+1, listeners[i]);
		}

		log.error("事件的详细信息:");
		log.error(ToBeRemoved.deepToString(event));
	}
}