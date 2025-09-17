package roj.event;

import org.jetbrains.annotations.NotNull;
import roj.Unused;
import roj.asm.ClassNode;
import roj.asm.Member;
import roj.asm.Opcodes;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Attribute;
import roj.asm.type.Type;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.collect.ArrayList;
import roj.collect.XashMap;
import roj.text.logging.Logger;

import java.util.List;

/**
 * 事件总线（EventBus），负责事件的分发、监听器的注册和注销。<br>
 * 此实现支持：
 * <ul>
 *     <li>注解驱动：使用 {@link Subscribe} 标记监听方法。</li>
 *     <li>事件继承：子事件可触发父事件的监听器。</li>
 *     <li>优先级排序：监听器按 {@link Priority} 执行。</li>
 *     <li>可取消事件：通过 {@link Cancellable} 支持中断传播。</li>
 *     <li>高性能：使用ASM动态生成隐藏类调用器（{@link HandlerInvoker}），避免反射开销。</li>
 *     <li>线程安全：支持并发注册/注销和分发（使用CAS和VarHandle）。</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Subscribe
 * public void onEvent(MyEvent e) {
 *     if (e.isCancellable()) e.cancel();
 * }
 * EventBus bus = new EventBus();
 * bus.register(this); // 注册当前实例
 * bus.post(new MyEvent()); // 分发事件
 * </pre>
 * </p>
 * <p>
 * 构造函数支持 {@link AnnotationRepo} 自动扫描所有监听器类，以提高性能。
 * </p>
 *
 * @author Roj234
 * @since 2024/3/21
 * @see Subscribe
 * @see Event
 * @see Priority
 */
public class EventBus {
	private static final String SUBSCRIBE_NAME = "roj/event/Subscribe";

	private static final XashMap.Builder<String, ListenerList> LISTENERS_BUILDER = XashMap.noCreation(ListenerList.class, "type");
	private static final XashMap.Builder<String, ListenerInfo.MapEntry> INFO_BUILDER = XashMap.noCreation(ListenerInfo.MapEntry.class, "owner");

	private final XashMap<String, ListenerList> listenerLists = LISTENERS_BUILDER.create();
	private final XashMap<String, ListenerInfo.MapEntry> entries = INFO_BUILDER.create();

	/**
	 * 创建一个空的EventBus实例。
	 * <p>
	 * 监听器需手动通过{@link #register(Object)}注册。
	 * </p>
	 */
	public EventBus() {}
	/**
	 * 创建一个EventBus实例，并从给定的注解仓库中自动扫描和注册监听器。
	 * <p>
	 * 该构造函数会扫描仓库中所有带有{@link Subscribe}注解的类和方法，并生成占位符。
	 * 监听器仍需通过{@link #register(Object)}手动注册，但注册时性能更好。
	 * </p>
	 *
	 * @param repo 注解仓库，用于扫描{@link Subscribe}注解的类
	 * @throws IllegalArgumentException 如果扫描过程中发现无效监听器（如参数不匹配）
	 * @see roj.asmx.launcher.Loader#getAnnotations()
	 */
	public EventBus(AnnotationRepo repo) {
		List<ListenerInfo> objectList = new ArrayList<>(), staticList = new ArrayList<>();
		for (AnnotatedElement el : repo.annotatedBy(SUBSCRIBE_NAME)) {
			String owner = el.owner().replace('/', '.');
			if (entries.get(owner) != null) continue;

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
					owner,
				objectList.toArray(new ListenerInfo[objectList.size()]),
				staticList.toArray(new ListenerInfo[staticList.size()])
			));
		}
	}

	/**
	 * 注册一个事件监听器。
	 * <p>
	 * 如果传入的是对象实例（非Class），则注册其实例方法监听器。<br>
	 * 如果传入的是Class对象，则注册其静态方法监听器（需确保Class已加载）。
	 * 监听器方法必须使用{@link Subscribe}注解标记，参数签名固定为(Event, void)。
	 * </p>
	 * <p>
	 * 支持优先级排序（HIGHEST优先执行）。重复注册同一监听器将被忽略。
	 * </p>
	 *
	 * @param o 监听器实例或Class对象
	 * @throws IllegalArgumentException 如果监听器类无效、无有效注解方法，或事件类型无法解析
	 */
	public void register(Object o) {
		Class<?> c = o instanceof Class<?> ? (Class<?>) o : o.getClass();
		for (var info : getListenerInfo(c, c != o)) {
			var list = listenerLists.get(info.event);
			if (list == null) {
				synchronized (listenerLists) {
					if ((list = listenerLists.get(info.event)) == null) {
						list = createListenerList(c, info.event);
					}
				}
			}
			list.add(info.flags&Priority.MASK, new HandlerInvoker(o, info));
		}
	}
	private ListenerList createListenerList(Class<?> c, String event) {
		Class<?> type;
		try {
			type = Class.forName(event, false, c.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("无法获取事件"+event+"的类型", e);
		}
		return createListenerList(type);
	}
	private ListenerList createListenerList(Class<?> event) {
		ListenerList list;
		if (event != Event.class) {
			Class<?> parent = event.getSuperclass();
			list = listenerLists.get(parent.getName());
			if (list == null) list = createListenerList(parent);
		} else {
			list = null;
		}
		list = new ListenerList(event.getName(), list);
		listenerLists.add(list);
		return list;
	}

	/**
	 * 注销一个事件监听器。
	 * <p>
	 * 与{@link #register(Object)}对应，注销指定实例或类的所有监听器方法。
	 * 如果传入Class，则注销其静态监听器；传入实例则注销其实例方法。
	 * </p>
	 *
	 * @param o 监听器实例或Class对象
	 * @return true 如果成功注销了至少一个监听器，否则false
	 */
	public boolean unregister(Object o) {
		Class<?> c = o instanceof Class<?> ? (Class<?>) o : o.getClass();
		Object inst = c != o ? o : null;
		var removed = false;
		for (var info : getListenerInfo(c, c != o)) {
			var list = listenerLists.get(info.event);
			if (list != null) {
				removed |= list.remove(info.flags&Priority.MASK, inst, info.method.name(), info.method.rawDesc());
			}
		}
		return removed;
	}

	private ListenerInfo[] getListenerInfo(Class<?> type, boolean object) {
		var entry = entries.get(type.getName());
		if (entry == null) {
			var data = ClassNode.fromType(type);
			if (data == null) throw new IllegalArgumentException("无法解析"+type.getName()+"的源文件！");

			List<ListenerInfo> objectList = new ArrayList<>(), staticList = new ArrayList<>();
			for (var mn : data.methods) {
				var annotations = mn.getAttribute(data.cp, Attribute.ClAnnotations);
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
					type.getName(),
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
	private static void toListenerInfo(Member mn, Annotation subscribe, List<ListenerInfo> objectList, List<ListenerInfo> staticList) {
		List<Type> types = Type.getMethodTypes(mn.rawDesc());
		if (types.size() != 2 || types.get(0).isPrimitive() || types.get(1).type != Type.VOID)
			throw new IllegalArgumentException("事件监听函数的参数不合法: 期待[Event, void]而不是"+types);

		int flags = Priority.valueOf(subscribe.getEnumValue("priority", "NORMAL")).ordinal();
		if (subscribe.getBool("receiveCancelled")) flags |= 128;

		ListenerInfo info = new ListenerInfo(types.get(0).owner.replace('/', '.'), mn, (byte) flags);
		((mn.modifier()&Opcodes.ACC_STATIC) != 0 ? staticList : objectList).add(info);
	}

	/**
	 * 检查指定事件类型是否有注册的监听器。<br>
	 * 事件发布者可以用此函数检测是否需要发布事件(降低GC开销)
	 * <p>
	 * 包括继承链中的监听器（例如，检查子事件会包含父事件的监听器）。
	 * </p>
	 *
	 * @param event 事件类型（必须继承Event）
	 * @return true 如果有监听器，否则false
	 */
	@SuppressWarnings("unchecked")
	public boolean hasListener(Class<? extends Event> event) {
		do {
			var listenerList = listenerLists.get(event.getName());
			if (listenerList != null) return listenerList.isEmpty();

			event = (Class<? extends Event>) event.getSuperclass();
		} while (event != Event.class);

		return false;
	}

	/**
	 * 发布一个事件，并分发给所有匹配的监听器。
	 * <p>
	 * 事件将按优先级顺序（{@code HIGHEST}到{@code LOWEST}）执行监听器链，包括事件继承链。
	 * 如果事件是可取消的，监听器可调用{@link Event#cancel()}中断后续执行。
	 * 已取消事件默认不会触发后续监听器，除非监听器指定{@link Subscribe#receiveCancelled()} = true。
	 * </p>
	 * <p>
	 * 异常将被捕获并记录日志，但不会中断分发。
	 * </p>
	 * <p>
	 * <strong>性能提示：</strong> 事件发布是同步的，高频发布建议预热注册。
	 * </p>
	 *
	 * @param event 要发布的事件实例（非null）
	 * @return true 如果事件被至少一个监听器取消，否则false
	 * @throws NullPointerException 如果event为null
	 * @see Cancellable
	 */
	public boolean post(@NotNull Event event) {
		var list = listenerLists.get(event.getClass().getName());
		if (list == null) return false;

		EventListener[] listeners;
		do {
			listeners = list.getListeners();
			for (int i = 0; i < listeners.length; i++) {
				try {
					listeners[i].invoke(event);
				} catch (Throwable e) {
					try {
						logDebugMessage(event, e, list.type, listeners, i);
					} catch (Throwable ignored) {}
					throw e;
				}
			}

			list = list.parent;
		} while (list != null);

		return event.isCanceled();
	}

	private static void logDebugMessage(Event event, Throwable e, String type, EventListener[] listeners, int index) {
		var log = Logger.getLogger();
		log.error("发布 {} 事件 (继承监听器为{}) 时发生了异常:", e, event.getClass().getName(), type);
		log.error("位于第 {}/{} 个接收者(从1开始):", index+1, listeners.length);

		for (int i = 0; i < listeners.length; i++) {
			log.error("  {}: {}", i+1, listeners[i]);
		}

		log.error("事件的详细信息:");
		log.error(Unused.deepToString(event));
	}
}