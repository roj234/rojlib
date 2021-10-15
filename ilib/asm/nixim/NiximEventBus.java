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

import ilib.asm.util.InvokerCompressor;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashMap;
import roj.reflect.ReflectionUtils;
import roj.util.EmptyArrays;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/14 15:49
 */
@Nixim("net.minecraftforge.fml.common.eventhandler.EventBus")
public abstract class NiximEventBus {
    @Shadow("listeners")
    private ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners;
    @Shadow("busID")
    private int busID;

    @Inject("register")
    public void register(Object target) {
        if (!this.listeners.containsKey(target)) {
            ModContainer active = Loader.instance().activeModContainer();
            if (active == null) {
                FMLLog.log.error("Unable to determine registrant mod for {}. This is a critical error and should be impossible", target, new Throwable());
                active = Loader.instance().getMinecraftModContainer();
            }

            boolean isStatic = target.getClass() == Class.class;
            Iterable<Class<?>> supers = isStatic ? Collections.singleton((Class<?>) target) : ReflectionUtils.getFathers(target);
            Map<String, Method> map = new MyHashMap<>();
            for (Class<?> parent : supers) {
                Method[] methods = parent.getDeclaredMethods();
                for (Method method : methods) {
                    if (isStatic == Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1) {
                        final Class<?> parameterType = method.getParameterTypes()[0];
                        if (method.isAnnotationPresent(SubscribeEvent.class)) {
                            if (!Event.class.isAssignableFrom(parameterType)) {
                                throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation, but takes a argument that is not an Event " + parameterType);
                            }
                            String id = method.getName() + '|' + parameterType.getName();
                            if (!map.containsKey(id)) {
                                map.put(id, method);
                            }
                        }
                    }
                }
            }

            for (Method method : (isStatic ? (Class<?>) target : target.getClass()).getMethods()) {
                if (isStatic == Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1) {
                    final Class<?> type = method.getParameterTypes()[0];
                    if (method.isAnnotationPresent(SubscribeEvent.class)) {
                        if (!Event.class.isAssignableFrom(type)) {
                            throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation, but takes a argument that is not an Event " + type);
                        }

                        Method real = map.get(method.getName() + '|' + type.getName());
                        if (real != null)
                            this.register(type, target, real, active);
                    }
                }
            }
        }
    }

    @Copy
    static ListenerList DEFAULT_LISTENER_LIST;

    @Inject("register")
    private void register(Class<?> eventType, Object target, Method method, final ModContainer owner) {
        try {
            ListenerList list;
            //if(!eventType.getName().startsWith("net.minecraftforge.")) {
            Constructor<?> ctr = eventType.getConstructor(EmptyArrays.CLASSES);
            ctr.setAccessible(true);
            Event e = (Event) ctr.newInstance();
            list = e.getListenerList();
            //} else {
            //    if(DEFAULT_LISTENER_LIST == null)
            //        DEFAULT_LISTENER_LIST = new Event().getListenerList();
            //    list = DEFAULT_LISTENER_LIST;
            //}

            final Object[] arr = EventInvokerV2.create(target, method, IGenericEvent.class.isAssignableFrom(eventType));

            IEventListener asm = (IEventListener) arr[1];

            IEventListener listener = asm;

            if (IContextSetter.class.isAssignableFrom(eventType)) {
                listener = event -> {
                    final Loader loader = Loader.instance();
                    ModContainer old = loader.activeModContainer();

                    loader.setActiveModContainer(owner);
                    ((IContextSetter) event).setModContainer(owner);

                    asm.invoke(event);

                    loader.setActiveModContainer(old);
                };
            }

            list.register(this.busID, (EventPriority) arr[0], listener);
            listeners.computeIfAbsent(target, (k) -> {
                ArrayList<IEventListener> listeners = new ArrayList<>(2);
                listeners.add(new InvokerCompressor(listeners, list, busID));
                return listeners;
            }).add(listener);
        } catch (Exception var10) {
            FMLLog.log.error("Error registering event handler: {} {} {}", owner, eventType, method, var10);
        }
    }

    public void unregister(Object object) {
        ArrayList<IEventListener> list = this.listeners.remove(object);
        if (list != null) {
            if (!(list.get(0) instanceof InvokerCompressor)) {
                FMLLog.bigWarning("[Implib-事件优化]: 可能无法取消注册Combined Event");
            }
            for (IEventListener listener : list) {
                ListenerList.unregisterAll(this.busID, listener);
            }
        }
    }
}
