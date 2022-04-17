/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ilib.asm.nixim;

import ilib.Config;
import ilib.asm.util.EventInvokerV2;
import ilib.asm.util.InvokerCompressor;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.asm.type.ParamHelper;
import roj.collect.MyHashMap;
import roj.reflect.ReflectionUtils;
import roj.util.EmptyArrays;
import roj.util.Helpers;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import net.minecraftforge.fml.common.eventhandler.*;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj234
 * @since  2020/11/14 15:49
 */
@Nixim("net.minecraftforge.fml.common.eventhandler.EventBus")
abstract class NxEventBus {
    @Shadow("listeners")
    private ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners;
    @Shadow("busID")
    private int busID;

    @Copy
    static Map<String, Map<String, Map<String, Object>>> annotated;

    @Inject("register")
    public void register(Object target) {
        if (!this.listeners.containsKey(target)) {
            ModContainer active = Loader.instance().activeModContainer();
            if (active == null) {
                FMLLog.log.error("Unable to determine registrant mod for {}. This is a critical error and should be impossible", target, new Throwable());
                active = Loader.instance().getMinecraftModContainer();
            }

            if (annotated == null) buildCache();

            boolean isStatic = target.getClass() == Class.class;

            Map<String, Map<String, Object>> sum = new MyHashMap<>();

            List<Class<?>> fathers = ReflectionUtils.getFathers(target);
            for (int i = fathers.size() - 1; i >= 0; i--) {
                Class<?> parent = fathers.get(i);
                Map<String, Map<String, Object>> clazz = annotated.get(parent.getName());
                if (clazz != null) {
                    sum.putAll(clazz);
                }
            }

            if (sum.isEmpty()) {
                if ((Config.debug & 16) != 0) FMLLog.bigWarning("注册的监听器没有事件: " + target);
                return;
            }

            for (Method method : (isStatic ? (Class<?>) target : target.getClass()).getMethods()) {
                if (isStatic == Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1) {
                    Class<?>[] types = method.getParameterTypes();
                    Map<String, Object> map = sum.get(method.getName() + ParamHelper.class2asm(types, void.class));

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
        Set<ASMDataTable.ASMData> dataset = ilib.asm.Loader.ASMTable.getAll("net.minecraftforge.fml.common.eventhandler.SubscribeEvent");
        for (ASMDataTable.ASMData data : dataset) {
            annotated.computeIfAbsent(data.getClassName(), Helpers.fnMyHashMap()).put(data.getObjectName(), data.getAnnotationInfo());
        }
    }

    @Copy
    private void register(Class<?> type, Object target, Method method, ModContainer owner, Map<String, Object> map) {
        try {
            Constructor<?> ctr = type.getConstructor(EmptyArrays.CLASSES);
            ctr.setAccessible(true);
            Event e = (Event) ctr.newInstance();
            ListenerList list = e.getListenerList();

            Type filter = null;
            if (IGenericEvent.class.isAssignableFrom(type)) {
                Type gType = method.getGenericParameterTypes()[0];
                if (gType instanceof ParameterizedType) {
                    filter = ((ParameterizedType) gType).getActualTypeArguments()[0];
                }
            }

            ModAnnotation.EnumHolder h = (ModAnnotation.EnumHolder) map.get("priority");
            String prn = h == null ? "NORMAL" : h.getValue();
            EventPriority priority = EventPriority.valueOf(prn);

            EventInvokerV2 asm = new EventInvokerV2(method, target instanceof Class ? null : target, filter,
                                                    InvokerCompressor.getInstance(list, busID),
                                                    priority, (Boolean) map.getOrDefault("", false));

            if (IContextSetter.class.isAssignableFrom(type)) {
                asm.setContext(owner);
            }

            ArrayList<IEventListener> list1 = listeners.get(target);
            if (list1 == null) {
                listeners.put(target, list1 = new ArrayList<>(4));
                if (Config.eventInvokerMost && !IGenericEvent.class.isAssignableFrom(type)) {
                    InvokerCompressor ic = new InvokerCompressor(list1, list, busID);
                    list1.add(ic);
                    list.register(busID, EventPriority.HIGHEST, ic);
                }
            }

            list1.add(asm);
            list.register(busID, priority, asm);
        } catch (Exception e) {
            FMLLog.log.error("Error registering event handler: {} {} {}", owner, type, method, e);
        }
    }

    public void unregister(Object object) {
        ArrayList<IEventListener> list = this.listeners.remove(object);
        if (list != null) {
            if (Config.eventInvokerMost && !(list.get(0) instanceof InvokerCompressor)) {
                FMLLog.bigWarning("[Implib-事件优化]: 可能无法取消注册Combined Event");
            }
            for (IEventListener listener : list) {
                ListenerList.unregisterAll(this.busID, listener);
            }
        }
    }
}
