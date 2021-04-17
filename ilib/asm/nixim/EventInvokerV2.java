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

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/14 16:15
 */

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.CstUTF;
import roj.asm.tree.Clazz;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrSourceFile;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.IfInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;
import roj.reflect.InstantiationUtil;
import roj.util.ByteList;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.IGenericEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventInvokerV2 implements IEventListener {
    private Object handler;
    // boolean 与 byte 空间一样
    private final byte flag;
    private final Type filter;

    static final AtomicInteger id = new AtomicInteger(0);

    public static Object[] create(Object target, Method method, boolean isGeneric) {
        SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);

        Type filter = null;
        if (isGeneric) {
            Type type = method.getGenericParameterTypes()[0];
            if (type instanceof ParameterizedType) {
                filter = ((ParameterizedType) type).getActualTypeArguments()[0];
            }

        }
        return new Object[]{annotation.priority(), new EventInvokerV2(method, target, filter, (byte) ((annotation.receiveCanceled() ? 64 : 0) | annotation.priority().ordinal()))};
    }

    public static IEventListener compressAll(List<EventInvokerV2> value, boolean ignoreCancelled) {
        value.sort((o1, o2) -> {
            int m1 = ((Method)((Object[])o1.handler)[0]).getModifiers();
            int m2 = ((Method)((Object[])o2.handler)[0]).getModifiers();
            return Modifier.isStatic(m1) == Modifier.isStatic(m2) ? 0 : (
                    Modifier.isStatic(m1) ? -1 : 1
                    );
        }); // 把static放前面

        Object[] targets = new Object[value.size()];
        Method[] methods = new Method[value.size()];

        for (int i = 0; i < value.size(); i++) {
            EventInvokerV2 v2 = value.get(i);
            Object[] arr = (Object[]) v2.handler;

            if (!Modifier.isStatic((methods[i] = (Method) arr[0]).getModifiers())) {
                targets[i] = arr[1];
            }
        }

        Clazz data = new Clazz();

        DirectAccessor.makeHeader("ilib/eh2/merged_" + id.getAndIncrement(), HANDLER_DESC, data);
        data.attributes.add(new AttrSourceFile(".merged_dynamic"));

        final FlagList flags = new FlagList(AccessFlag.PUBLIC);

        data.fields.add(new roj.asm.tree.Field(flags, "i", "[Ljava/lang/Object;"));

        roj.asm.tree.Method init = new roj.asm.tree.Method(flags, data, "<init>", "([Ljava/lang/Object;)V");
        data.methods.add(init);
        AttrCode code = init.code = new AttrCode(init);

        code.stackSize = code.localSize = 2;
        InsnList insns = code.instructions;

        insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insns.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, DirectAccessor.MAGIC_ACCESSOR_CLASS + ".<init>:()V"));

        insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insns.add(NodeHelper.npc(Opcodes.ALOAD_1));
        insns.add(new FieldInsnNode(Opcodes.PUTFIELD, data, 0));

        insns.add(NodeHelper.npc(Opcodes.RETURN));

        roj.asm.tree.Method invoke = new roj.asm.tree.Method(flags, data, "invoke", HANDLER_FUNC_DESC);
        data.methods.add(invoke);
        code = invoke.code = new AttrCode(invoke);

        code.stackSize = 3; // array object event
        code.localSize = 2; // self event
        insns = code.instructions;
        LabelInsnNode label = null;
        if(ignoreCancelled) {
            code.interpretFlags = AttrCode.COMPUTE_FRAMES | AttrCode.COMPUTE_SIZES;
            insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
            insns.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/common/eventhandler/Event", "isCanceled", "()Z"));
            insns.add(new IfInsnNode(Opcodes.IFNE, label = new LabelInsnNode()));
        }

        boolean ready = false;

        for (int i = 0; i < targets.length; i++) {
            Method method = methods[i];

            String clz = method.getDeclaringClass().getName().replace('.', '/');
            String signature = "(L" + method.getParameterTypes()[0].getName().replace('.', '/') + ";)V";

            Object obj = targets[i];

            if(obj == null) {
                if(!ready) {
                    insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
                    insns.add(new FieldInsnNode(Opcodes.GETFIELD, data, 0));
                    ready = true;
                    //缓存array
                }
                insns.add(NodeHelper.npc(Opcodes.DUP));
                insns.add(NodeHelper.loadInt(i));
                insns.add(NodeHelper.npc(Opcodes.AALOAD));
                insns.add(NodeHelper.npc(Opcodes.ALOAD_1));
                insns.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, clz, method.getName(), signature));
            } else {
                insns.add(NodeHelper.npc(Opcodes.ALOAD_1));
                insns.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, clz, method.getName(), signature));
            }
        }

        if(ready) {
            insns.add(NodeHelper.npc(Opcodes.POP));
        }

        if(label != null)
            insns.add(label);
        insns.add(NodeHelper.npc(Opcodes.RETURN));

        ByteList list = Parser.toByteArrayShared(data);

        Class<?> clazz = ClassDefiner.INSTANCE.defineClassC(data.name.replace('/', '.'), list.list, list.offset(), list.limit());

        try {
            return (IEventListener) InstantiationUtil.createClass(clazz, new Class<?>[]{Object[].class}, targets);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new InternalError(e);
        }
    }

    public boolean canCompress() {
        return filter == null && !(handler instanceof IEventListener);
    }

    public IEventListener compress() {
        try {
            Object[] arr = (Object[]) handler;
            this.handler = new boolean[0];
            return createListener((Method) arr[0], arr[1]);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("EventInvoker Lazy initialization failed!", e);
        }
    }

    private EventInvokerV2(Method method, Object target, Type generic, byte flag) {
        handler = new Object[]{method, target};
        this.flag = flag;
        filter = generic;
    }

    public void invoke(Event event) {
        if (!(handler instanceof IEventListener)) {
            Object[] arr = (Object[]) handler;
            try {
                handler = createListener((Method) arr[0], arr[1]);
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new RuntimeException("EventInvoker Lazy initialization failed!", e);
            }
        }

        if (((flag & 64) == 0 /*|| !event.isCancelable()*/ || !event.isCanceled()) && (this.filter == null || this.filter == ((IGenericEvent<?>) event).getGenericType())) {
            ((IEventListener) handler).invoke(event);
        }
    }

    public String toString() {
        return "EI2: " + (handler instanceof Object[] ? (Arrays.toString((Object[])handler)) : handler.getClass().getName());
    }

    private static final String HANDLER_DESC = "net/minecraftforge/fml/common/eventhandler/IEventListener";
    private static final String HANDLER_FUNC_DESC = "(Lnet/minecraftforge/fml/common/eventhandler/Event;)V";
    /*

    public static void main(String[] args) throws IOException {

        {
            Clazz clazz = new Clazz();
            DirectMethodAccess.makeHeader("u", HANDLER_DESC, clazz);
            clazz.attributes.add(new AttrSourceFile(".dynamic"));

            final FlagList flags = new FlagList(AccessFlag.PUBLIC);

            clazz.fields.add(new Field(flags, "i", "Ljava/lang/Object;"));

            roj.asm.struct.Method init = new roj.asm.struct.Method(flags, clazz, "<init>", "(Ljava/lang/Object;)V");
            clazz.methods.add(init);
            AttrCode code = init.code = new AttrCode(init);

            code.stackLength = code.localLength = 2;
            InsnList insns = code.instructions;

            insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
            insns.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, DirectMethodAccess.MAGIC_ACCESSOR_CLASS + ".<init>:()V"));

            insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
            insns.add(NodeHelper.npc(Opcodes.ALOAD_1));
            insns.add(new FieldInsnNode(Opcodes.PUTFIELD, clazz, 0));

            insns.add(NodeHelper.npc(Opcodes.RETURN));
            insns.add(AttrCode.METHOD_END_MARK);

            roj.asm.struct.Method invoke = new roj.asm.struct.Method(flags, clazz, "invoke", HANDLER_FUNC_DESC);
            clazz.methods.add(invoke);
            code = invoke.code = new AttrCode(invoke);

            code.stackLength = 2;
            code.localLength = 2;
            insns = code.instructions;

            insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
            insns.add(new FieldInsnNode(Opcodes.GETFIELD, clazz, 0));
            insns.add(NodeHelper.npc(Opcodes.ALOAD_1));
            insns.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "hl", "mn", "(Lcn;)V"));

            insns.add(NodeHelper.npc(Opcodes.RETURN));
            insns.add(AttrCode.METHOD_END_MARK);

            try (FileOutputStream fos = new FileOutputStream(new File("object.class"))) {
                clazz.getBytes().writeToStream(fos);
            }
        }

        Clazz clazz = new Clazz();
        DirectMethodAccess.makeHeader("u", HANDLER_DESC, clazz);
        clazz.attributes.add(new AttrSourceFile(".dynamic"));

        final FlagList flags = new FlagList(AccessFlag.PUBLIC);

        roj.asm.struct.Method init = new roj.asm.struct.Method(flags, clazz, "<init>", "()V");
        clazz.methods.add(init);
        AttrCode code = init.code = new AttrCode(init);

        code.stackLength = code.localLength = 1;
        InsnList insns = code.instructions;

        insns.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insns.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, DirectMethodAccess.MAGIC_ACCESSOR_CLASS + ".<init>:()V"));

        insns.add(NodeHelper.npc(Opcodes.RETURN));
        insns.add(AttrCode.METHOD_END_MARK);

        roj.asm.struct.Method invoke = new roj.asm.struct.Method(flags, clazz, "invoke", HANDLER_FUNC_DESC);
        clazz.methods.add(invoke);
        code = invoke.code = new AttrCode(invoke);

        code.stackLength = 1;
        code.localLength = 2;
        insns = code.instructions;

        insns.add(NodeHelper.npc(Opcodes.ALOAD_1));
        insns.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "hl", "mn", "(Lcn;)V"));

        insns.add(NodeHelper.npc(Opcodes.RETURN));
        insns.add(AttrCode.METHOD_END_MARK);

        try (FileOutputStream fos = new FileOutputStream(new File("static.class"))) {
            clazz.getBytes().writeToStream(fos);
        }
    }*/

    public static IEventListener createListener(Method method, Object target) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        final Class<?> clazz = createWrapper(method);
        if (Modifier.isStatic(method.getModifiers())) {
            return (IEventListener) InstantiationUtil.createClass(clazz);
        } else {
            return (IEventListener) InstantiationUtil.createClass(clazz, new Class<?>[]{Object.class}, target);
        }
    }

    private static final MyHashMap<Method, Class<?>> cache = new MyHashMap<>();

    public static Class<?> createWrapper(Method callback) {
        Class<?> clz = cache.get(callback);
        if (clz != null) {
            return clz;
        } else {
            boolean isStatic = Modifier.isStatic(callback.getModifiers());
            ConstantData data = getData(isStatic);

            ByteList list;
            String name = getUniqueName(callback);

            synchronized (data) {
                data.nameCst.getValue().setString(name.replace('.', '/'));

                int i = isStatic ? 5 : 0;

                ((CstUTF) data.cp.array(19 - i)).setString(callback.getDeclaringClass().getName().replace('.', '/'));
                ((CstUTF) data.cp.array(21 - i)).setString(callback.getName());
                ((CstUTF) data.cp.array(22 - i)).setString("(L" + callback.getParameterTypes()[0].getName().replace('.', '/') + ";)V");

                list = Parser.toByteArrayShared(data);
            }

            Class<?> ret = ClassDefiner.INSTANCE.defineClassC(name, list.list, list.offset(), list.limit());
            cache.put(callback, ret);
            return ret;
        }
    }

    private static ConstantData objectData, staticData;

    private static ConstantData getData(boolean isStatic) {
        if (objectData == null) {
            try {
                objectData = Parser.parseConstants(IOUtil.read(EventInvokerV2.class, "META-INF/nixim/eh/object.class"));
                staticData = Parser.parseConstants(IOUtil.read(EventInvokerV2.class, "META-INF/nixim/eh/static.class"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return isStatic ? staticData : objectData;
    }

    private static String getUniqueName(Method callback) {
        return "ilib.eh2." + callback.getDeclaringClass().getSimpleName() + '_' + callback.getName() + id.getAndIncrement();
    }

    public int getPriority() {
        return flag & 63;
    }

    public boolean receiveCanceled() {
        return (flag & 64) != 0;
    }
}

