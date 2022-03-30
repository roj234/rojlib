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
package ilib.asm.util;

/**
 * @author Roj234
 * @since  2020/11/14 16:15
 */

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import roj.asm.Opcodes;
import roj.asm.tree.Clazz;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrSourceFile;
import roj.asm.tree.insn.*;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.reflect.DirectAccessor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static ilib.asm.util.InvokerCompressor.Helper;
import static roj.asm.util.AccessFlag.PUBLIC;

public final class EventInvokerV2 implements IEventListener {
    private final Object handler;
    private final Method method;
    private final Type filter;
    private final Object owner;
    private ModContainer mc;
    public final boolean recvCancel;
    private IEventListener built;

    static final AtomicInteger id = new AtomicInteger(0);

    public static IEventListener batch(List<EventInvokerV2> value, boolean ignoreCancelled) {
        value.sort((o1, o2) -> {
            int m1 = o1.method.getModifiers();
            int m2 = o2.method.getModifiers();
            return Modifier.isStatic(m1) == Modifier.isStatic(m2) ? 0 : (
                    Modifier.isStatic(m1) ? -1 : 1);
        }); // 把static放前面

        boolean haveDyn = false;
        Object[] targets = new Object[value.size()];
        Method[] methods = new Method[value.size()];

        for (int i = 0; i < value.size(); i++) {
            EventInvokerV2 v2 = value.get(i);

            if (!Modifier.isStatic((methods[i] = v2.method).getModifiers())) {
                targets[i] = v2.handler;
                haveDyn = true;
            }
        }

        Clazz cz = new Clazz();

        DirectAccessor.makeHeader("ilib/BatchEventInvoker$" + id.getAndIncrement(), HANDLER_DESC, cz);
        DirectAccessor.addInit(cz);
        cz.interfaces.add("ilib/asm/util/EventInvokerV2$INIT");

        cz.attributes.add(new AttrSourceFile("ImpLib合并的事件执行者"));

        if (haveDyn) {
            addField(cz, "[Ljava/lang/Object;", "setArr", "(Ljava/lang/Object;)V");
        }

        roj.asm.tree.Method m0 = new roj.asm.tree.Method(PUBLIC, cz, "invoke", HANDLER_FUNC_DESC);
        cz.methods.add(m0);
        AttrCode c0 = m0.code = new AttrCode(m0);

        c0.stackSize = 3; // array object event
        c0.localSize = 2; // self event

        InsnList insn = c0.instructions;
        LabelInsnNode label= new LabelInsnNode();

        if(ignoreCancelled) c0.interpretFlags = AttrCode.COMPUTE_FRAMES;

        boolean ready = false;

        for (int i = 0; i < targets.length; i++) {
            Method method = methods[i];

            String clz = method.getDeclaringClass().getName().replace('.', '/');
            String signature = "(L" + method.getParameterTypes()[0].getName().replace('.', '/') + ";)V";

            Object obj = targets[i];

            if (ignoreCancelled) {
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/common/eventhandler/Event", "isCanceled", "()Z"));
                insn.add(new IfInsnNode(Opcodes.IFNE, label));
            }

            if(obj != null) {
                if(!ready) {
                    insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                    insn.add(new FieldInsnNode(Opcodes.GETFIELD, cz, 0));
                    ready = true;
                    //缓存array
                }
                insn.add(NPInsnNode.of(Opcodes.DUP));
                insn.add(NodeHelper.loadInt(i));
                insn.add(NPInsnNode.of(Opcodes.AALOAD));
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, clz, method.getName(), signature));
            } else {
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, clz, method.getName(), signature));
            }

        }

        if(ready) insn.add(NPInsnNode.of(Opcodes.POP));

        if(ignoreCancelled) insn.add(label);
        insn.add(NPInsnNode.of(Opcodes.RETURN));

        INIT o = (INIT) DirectAccessor.i_build(cz);
        if (haveDyn) o.setArr(targets);
        return o;
    }

    public void setContext(ModContainer owner) {
        this.mc = owner;
    }

    private interface INIT extends IEventListener {
        void setArr(Object arr);
        void setFilter(Type type);
        void setOwner(ModContainer owner);
        Object clone();
    }

    public boolean canBatch() {
        return filter == null;
    }

    public EventInvokerV2(Method method, Object target, Type generic, Object owner, EventPriority priority, boolean recvCancel) {
        this.method = method;
        this.handler = target;
        this.owner = owner;
        this.recvCancel = recvCancel;
        this.filter = generic;
    }

    @SuppressWarnings("unchecked")
    public void invoke(Event event) {
        IEventListener me = null;
        for (EventPriority priority : InvokerCompressor.priorities) {
            List<IEventListener> listeners = ((List<List<IEventListener>>) Helper.getListeners(owner)).get(priority.ordinal());
            for (int i = 0; i < listeners.size(); i++) {
                IEventListener listener = listeners.get(i);
                if (listener instanceof EventInvokerV2) {
                    IEventListener v = ((EventInvokerV2) listener).build();
                    listeners.set(i, v);
                    if (this == listener) me = v;
                }
            }
        }
        Helper.forceRebuild(owner);

        if (me == null) {
            if (built == null) built = me = build();
            else me = built;
        }
        me.invoke(event);
    }

    public String toString() {
        return "EI2: " + method;
    }

    private static final String HANDLER_DESC = "net/minecraftforge/fml/common/eventhandler/IEventListener";
    private static final String HANDLER_FUNC_DESC = "(Lnet/minecraftforge/fml/common/eventhandler/Event;)V";
    private static final ConcurrentHashMap<Method, INIT> cache = new ConcurrentHashMap<>();

    private IEventListener build() {
        Method method = this.method;

        INIT built = cache.get(method);
        if (built != null) {
            if (!(built instanceof Cloneable)) {
                return built;
            }

            built = (INIT) built.clone();
        } else {
            Clazz cz = new Clazz();

            String cn = getUniqueName(method);
            DirectAccessor.makeHeader(cn.replace('.', '/'), HANDLER_DESC, cz);
            DirectAccessor.addInit(cz);
            cz.interfaces.add("ilib/asm/util/EventInvokerV2$INIT");

            cz.attributes.add(new AttrSourceFile("ImpLib生成的事件执行者"));

            roj.asm.tree.Method m0 = new roj.asm.tree.Method(PUBLIC, cz, "invoke", HANDLER_FUNC_DESC);
            cz.methods.add(m0);
            AttrCode c0 = m0.code = new AttrCode(m0);

            c0.stackSize = c0.localSize = 2;

            InsnList insn = c0.instructions;
            LabelInsnNode label = new LabelInsnNode();

            String clz = method.getDeclaringClass().getName().replace('.', '/');
            String signature = "(L" + method.getParameterTypes()[0].getName().replace('.', '/') + ";)V";

            if (!recvCancel) {
                c0.interpretFlags = AttrCode.COMPUTE_FRAMES;

                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/common/eventhandler/Event", "isCanceled", "()Z"));
                insn.add(new IfInsnNode(Opcodes.IFNE, label));
            }

            if (filter != null) {
                DirectAccessor.cloneable(cz);

                c0.interpretFlags = AttrCode.COMPUTE_FRAMES;

                int fid = addField(cz, "java/lang/reflect/Type", "setFilter","(Ljava/lang/reflect/Type;)V");

                insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, cz, fid));
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeItfInsnNode("net/minecraftforge/fml/common/eventhandler/IGenericEvent",
                                            "getGenericType", "()Ljava/lang/reflect/Type;"));
                insn.add(new IfInsnNode(Opcodes.IF_acmpne, label));
            }

            if (mc != null) {
                if (!cz.interfaces.contains("java/lang/Cloneable")) {
                    DirectAccessor.cloneable(cz);
                }
                c0.stackSize = 3;
                c0.localSize = 4;

                int fid = addField(cz, "net/minecraftforge/fml/common/ModContainer", "setOwner", null);

                Loader loader = Loader.instance();
                insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "net/minecraftforge/fml/common/Loader",
                                            "instance", "()Lnet/minecraftforge/fml/common/Loader;"));
                insn.add(NPInsnNode.of(Opcodes.DUP));
                insn.add(NPInsnNode.of(Opcodes.ASTORE_2));

                // ModContainer old = loader.activeModContainer();
                insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "net/minecraftforge/fml/common/Loader",
                                            "activeModContainer", "()Lnet/minecraftforge/fml/common/ModContainer;"));
                insn.add(NPInsnNode.of(Opcodes.ASTORE_3));

                // loader.setActiveModContainer(owner);
                insn.add(NPInsnNode.of(Opcodes.ALOAD_2));
                insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, cz, fid));
                insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "net/minecraftforge/fml/common/Loader",
                                            "setActiveModContainer", "(Lnet/minecraftforge/fml/common/ModContainer;)V"));

                // ((IContextSetter) event).setModContainer(owner);
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, cz, fid));
                insn.add(new InvokeItfInsnNode("net/minecraftforge/fml/common/eventhandler/IContextSetter",
                                            "setModContainer", "(Lnet/minecraftforge/fml/common/ModContainer;)V"));
            }

            if(handler != null) {
                int fid = addField(cz, "java/lang/Object", "setArr", null);

                if (!cz.interfaces.contains("java/lang/Cloneable")) {
                    DirectAccessor.cloneable(cz);
                }

                insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, cz, fid));

                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, clz, method.getName(), signature));
            } else {
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, clz, method.getName(), signature));
            }

            if (mc != null) {
                // loader.setActiveModContainer(old);
                insn.add(NPInsnNode.of(Opcodes.ALOAD_2));
                insn.add(NPInsnNode.of(Opcodes.ALOAD_3));
                insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "net/minecraftforge/fml/common/Loader",
                                            "setActiveModContainer", "(Lnet/minecraftforge/fml/common/ModContainer;)V"));
            }

            if(!recvCancel) insn.add(label);
            insn.add(NPInsnNode.of(Opcodes.RETURN));

            built = (INIT) DirectAccessor.i_build(cz);
            cache.put(method, built);
        }

        if (handler != null) built.setArr(handler);
        if (filter != null) built.setFilter(filter);
        if (mc != null) built.setOwner(mc);
        return built;
    }

    private static int addField(Clazz cz, String type, String setName, String setDesc) {
        cz.fields.add(new roj.asm.tree.Field(PUBLIC, Integer.toString(cz.fields.size()), new roj.asm.type.Type(type)));

        roj.asm.tree.Method m1 = new roj.asm.tree.Method(PUBLIC, cz, setName, setDesc == null ? "(L" + type + ";)V" : setDesc);
        cz.methods.add(m1);
        AttrCode c1 = m1.code = new AttrCode(m1);

        c1.stackSize = c1.localSize = 2;

        InsnList insn1 = c1.instructions;
        insn1.add(NPInsnNode.of(Opcodes.ALOAD_0));
        insn1.add(NPInsnNode.of(Opcodes.ALOAD_1));
        insn1.add(new FieldInsnNode(Opcodes.PUTFIELD, cz, cz.fields.size() - 1));
        insn1.add(NPInsnNode.of(Opcodes.RETURN));

        return cz.fields.size() - 1;
    }

    private static String getUniqueName(Method callback) {
        return "ilib.eh2." + callback.getDeclaringClass().getSimpleName() + '_' + callback.getName() + '$' + id.getAndIncrement();
    }
}

