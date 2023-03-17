package ilib.asm.nx;

import ilib.Config;
import ilib.asm.util.EventInvokerV2;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.asm.type.TypeHelper;
import roj.collect.MyHashMap;
import roj.reflect.ReflectionUtils;
import roj.util.ArrayCache;
import roj.util.Helpers;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import net.minecraftforge.fml.common.eventhandler.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj234
 * @since 2020/11/14 15:49
 */
@Nixim("net.minecraftforge.fml.common.eventhandler.EventBus")
abstract class NxEventBus {
	@Shadow("/")
	private ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners;
	@Shadow("/")
	private int busID;

	@Copy
	static Map<String, Map<String, Map<String, Object>>> annotated;
	@Copy
	static Map<Class<?>, ListenerList> listenerManagers;

	@Inject("/")
	public void register(Object target) {
		if (!this.listeners.containsKey(target)) {
			ModContainer active = Loader.instance().activeModContainer();
			if (active == null) {
				FMLLog.log.error("Unable to determine registrant mod for {}. This is a critical error and should be impossible", target, new Throwable());
				active = Loader.instance().getMinecraftModContainer();
			}

			if (annotated == null) buildCache();

			boolean isStatic = target.getClass() == Class.class;

			Map<String, Map<String, Object>> sum;
			if (isStatic) {
				sum = annotated.getOrDefault(((Class<?>) target).getName(), Collections.emptyMap());
			} else {
				sum = new MyHashMap<>();

				List<Class<?>> fathers = ReflectionUtils.getFathers(target);
				for (int i = fathers.size() - 1; i >= 0; i--) {
					Class<?> parent = fathers.get(i);
					Map<String, Map<String, Object>> clazz = annotated.get(parent.getName());
					if (clazz != null) {
						sum.putAll(clazz);
					}
				}
			}

			if (sum.isEmpty()) {
				if ((Config.debug & 16) != 0) FMLLog.bigWarning("注册的监听器没有事件: " + target);
				return;
			}

			for (Method method : (isStatic ? (Class<?>) target : target.getClass()).getMethods()) {
				if (isStatic == Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1) {
					Class<?>[] types = method.getParameterTypes();
					Map<String, Object> map = sum.get(method.getName() + TypeHelper.class2asm(types, void.class));

					if (map != null) {
						if (!Event.class.isAssignableFrom(types[0])) {
							throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation, but takes a argument that is not an Event " + types[0]);
						}

						register(types[0], target, method, active, map);
					}
				}
			}
		}
	}

	@Copy
	protected static void buildCache() {
		annotated = new MyHashMap<>();
		listenerManagers = new MyHashMap<>();
		Set<ASMDataTable.ASMData> dataset = ilib.asm.Loader.Annotations.getAll("net.minecraftforge.fml.common.eventhandler.SubscribeEvent");
		for (ASMDataTable.ASMData data : dataset) {
			annotated.computeIfAbsent(data.getClassName(), Helpers.fnMyHashMap()).put(data.getObjectName(), data.getAnnotationInfo());
		}
	}

	@Copy
	private static ListenerList getManager(Class<?> type) throws ReflectiveOperationException {
		ListenerList manager = listenerManagers.get(type);
		if (manager == null) {
			Constructor<?> ctr = type.getConstructor(ArrayCache.CLASSES);
			ctr.setAccessible(true);
			manager = ((Event)ctr.newInstance()).getListenerList();
			listenerManagers.put(type, manager);
		}

		return manager;
	}

	@Copy
	private void register(Class<?> type, Object target, Method method, ModContainer owner, Map<String, Object> map) {
		try {
			ListenerList list = getManager(type);

			Type filter = null;
			if (IGenericEvent.class.isAssignableFrom(type)) {
				Type gType = method.getGenericParameterTypes()[0];
				if (gType instanceof ParameterizedType) {
					filter = ((ParameterizedType) gType).getActualTypeArguments()[0];
				}
			}

			ModAnnotation.EnumHolder h = (ModAnnotation.EnumHolder) map.get("priority");
			EventPriority priority = h == null ? EventPriority.NORMAL : EventPriority.valueOf(h.getValue());

			EventInvokerV2 asm = new EventInvokerV2(method, target instanceof Class ? null : target, filter, EventInvokerV2.Helper.getInstance(list, busID), priority,
													(Boolean) map.getOrDefault("receiveCanceled", false));

			if (IContextSetter.class.isAssignableFrom(type)) asm.setContext(owner);

			ArrayList<IEventListener> list1 = listeners.get(target);
			if (list1 == null) listeners.put(target, list1 = new ArrayList<>(4));
			asm.setList(list1);

			list1.add(asm);
			list.register(busID, priority, asm);
		} catch (Exception e) {
			FMLLog.log.error("Error registering event handler: {} {} {}", owner, type, method, e);
		}
	}

	@Inject("/")
	public void handleException(EventBus bus, Event event, IEventListener[] listeners, int index, Throwable throwable) {
		FMLLog.log.error("发布 {} 事件时发生了异常:", event.getClass().getName(), throwable);
		FMLLog.log.error("位于第 {}/{} 个接收者(begin with 1):", index+1, listeners.length);

		for (int i = 0; i < listeners.length; ++i) {
			FMLLog.log.error("{}: {}", i, listeners[i]);
		}

		FMLLog.log.error("事件的详细信息（由Implib倾情赞助）:");
		for (Field field : ReflectionUtils.getFields(event.getClass())) {
			try {
				if (!Modifier.isStatic(field.getModifiers())) {
					field.setAccessible(true);
					Object value = field.get(event);
					FMLLog.log.error("字段{}: {}", field.getName(), value);
				}
			} catch (ReflectiveOperationException ignored) {}
		}
	}
}
