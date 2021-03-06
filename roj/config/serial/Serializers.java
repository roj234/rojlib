/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.config.serial;

import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstString;
import roj.asm.cst.CstUTF;
import roj.asm.tree.Clazz;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnList;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.EmptyArrays;
import roj.util.Helpers;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.ARRAY;
import static roj.asm.type.Type.CLASS;
import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * @author Roj233
 * @since 2022/1/11 17:49
 */
public final class Serializers {
    interface Init {
        void init(Serializers instance);
    }

    public static final Serializers DEFAULT = new Serializers(0);

    final Map<String, Serializer<?>> registry = new MyHashMap<>();
    final ConcurrentLinkedQueue<Init> pending = new ConcurrentLinkedQueue<>();

    public static final int LENIENT = 1, NONSTATIC = 2, AUTOGEN = 4, NOINHERIT = 8;
    public int defaultFlag;

    @SuppressWarnings("unchecked")
    public <T> Serializer<T> find(String name) {
        Serializer<?> ser = registry.get(name);
        if (ser == null) {
            if ((defaultFlag & AUTOGEN) == 0) {
                System.out.println("Not serialize due to not AUTOGEN flag");
                return null;
            }
            Class<?> cls;
            try {
                cls = Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
            register(cls, defaultFlag);
            ser = registry.get(cls.getName());
            if (ser == null)
                throw new IllegalStateException("Recursive finding " + cls.getName());
        }

        return (Serializer<T>) ser;
    }

    public void register(Class<?> cls, int flag) {
        if (registry.containsKey(cls.getName())) return;
        synchronized (registry) {
            if (registry.containsKey(cls.getName())) return;
        }

        Serializer<?> ser;
        if ((defaultFlag & NOINHERIT) == 0) {
            List<Class<?>> clz = ReflectionUtils.getFathersAndItfOrdered(cls);
            for (int i = 1; i < clz.size(); i++) {
                ser = registry.get(clz.get(i).getName());
                if (ser != null) {
                    synchronized (registry) {
                        registry.putIfAbsent(cls.getName(), ser);
                    }
                    return;
                }
            }
        }

        synchronized (registry) {
            if (null != registry.putIfAbsent(cls.getName(), null)) return;
        }
        if (cls.isEnum()) {
            ser = new EnumSerializer(cls);
        } else if (cls.getComponentType() != null) {
            ser = arraySerializer(cls, flag);
        } else {
            ser = serializer(cls, flag);
        }
        synchronized (registry) {
            registry.put(cls.getName(), ser);
        }
        if (!pending.isEmpty()) {
            try {
                Init r;
                do {
                    r = pending.peek();
                    if (r != null) {
                        r.init(this);
                        pending.poll();
                    }
                } while (r != null);
            } catch (IllegalStateException ignored) {}
        }
        if (ser instanceof Init) {
            try {
                ((Init) ser).init(this);
            } catch (IllegalStateException e) {
                pending.add((Init) ser);
            }
        }
    }

    public void register(Class<?> cls, Serializer<?> ser) {
        synchronized (registry) {
            registry.put(cls.getName(), ser);
        }
    }

    public Serializers(int flag) {
        this();
        this.defaultFlag = flag;
    }

    public Serializers() {
        WrapSerializer s = new WrapSerializer(this);
        register(Object.class, s);
        register(Integer.class, s);
        register(Long.class, s);
        register(Double.class, s);
        register(Boolean.class, s);
        register(CharSequence.class, s);
        register(Float.class, new FloatSerializer());
        register(Character.class, new CharSerializer());
        register(Byte.class, new ByteSerializer());
        register(Short.class, new ShortSerializer());
        register(Map.class, new MapSerializer(this));

        register(List.class, s);
        register(Collection.class, s);
        register(Set.class, new SetSerializer(this));
    }

    private static final AtomicInteger ordinal = new AtomicInteger();

    static {
        try {
            byte[] read = IOUtil.read("roj/config/serial/GenSer.class");
            ConstantData d = Parser.parseConstants(read);
            for (Constant c : d.cp.array()) {
                if (c.type() == Constant.CLASS) {
                    CstUTF value = ((CstClass) c).getValue();
                    if (value.getString().equals("roj/config/serial/WrapSerializer")) // as a mark
                        value.setString(DirectAccessor.MAGIC_ACCESSOR_CLASS);
                }
            }
            ByteList t = Parser.toByteArrayShared(d);
            ClassDefiner.INSTANCE.defineClassC(d.name.replace('/', '.'),
                                               t.list, 0, t.wIndex());

            read = IOUtil.read("roj/config/serial/GenArraySer.class");
            d = Parser.parseConstants(read);
            for (Constant c : d.cp.array()) {
                if (c.type() == Constant.CLASS) {
                    CstUTF value = ((CstClass) c).getValue();
                    if (value.getString().equals("roj/config/serial/WrapSerializer")) // as a mark
                        value.setString(DirectAccessor.MAGIC_ACCESSOR_CLASS);
                }
            }
            t = Parser.toByteArrayShared(d);
            ClassDefiner.INSTANCE.defineClassC(d.name.replace('/', '.'),
                                               t.list, 0, t.wIndex());
        } catch (IOException e) {
            Helpers.athrow(e);
            throw null;
        }
    }

    private Serializer<?> serializer(Class<?> owner, int flag) {
        assert !owner.isEnum() && !owner.isPrimitive() && owner.getComponentType() == null;

        if (owner.isInterface()) {
            if ((flag & LENIENT) != 0) return UnableSerializer.INSTANCE;
            else throw new IllegalArgumentException("??????: ???????????????????????? ???????????????");
        }

        String className = owner.getName().replace('.', '/');

        Clazz cz = new Clazz();
        DirectAccessor.makeHeader("roj/config/serial/GenSer$" + ordinal.getAndIncrement(),
                                  "roj/config/serial/Serializer",
                                  cz);
        cz.interfaces.add("roj/config/serial/Serializers$Init");
        cz.parent = "roj/config/serial/GenSer";
        DirectAccessor.addInit(cz);

        Method m0 = new Method(PUBLIC, cz, "init", "(Lroj/config/serial/Serializers;)V");
        cz.methods.add(m0);
        AttrCode c0 = m0.code = new AttrCode(m0);
        c0.interpretFlags = AttrCode.COMPUTE_SIZES;
        c0.instructions.add(NPInsnNode.of(RETURN));

        m0 = new Method(PUBLIC, cz,
                               "serialize0", "(Lroj/config/data/CMapping;Ljava/lang/Object;)V");
        cz.methods.add(m0);
        c0 = m0.code = new AttrCode(m0);
        c0.interpretFlags = AttrCode.COMPUTE_SIZES;
        InsnList rcSer = c0.instructions;

        rcSer.add(NPInsnNode.of(ALOAD_2));
        rcSer.add(new ClassInsnNode(CHECKCAST, className));
        rcSer.add(NPInsnNode.of(ALOAD_2));

        rcSer.add(NPInsnNode.of(ALOAD_1));
        rcSer.add(new FieldInsnNode(GETFIELD, "roj/config/data/CMapping", "map", new Type("java/util/Map")));

        m0 = new Method(PUBLIC, cz, "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;");
        cz.methods.add(m0);
        c0 = m0.code = new AttrCode(m0);
        c0.stackSize = c0.localSize = 3;

        InsnList rcDes = c0.instructions;

        rcDes.add(NPInsnNode.of(ALOAD_1));
        rcDes.add(new ClassInsnNode(CHECKCAST, "roj/config/data/CMapping"));
        rcDes.add(new FieldInsnNode(GETFIELD, "roj/config/data/CMapping", "map", new Type("java/util/Map")));
        rcDes.add(NPInsnNode.of(ASTORE_1));

        rcDes.add(new ClassInsnNode(NEW, className));

        try {
            owner.getDeclaredConstructor(EmptyArrays.CLASSES);

            rcDes.add(NPInsnNode.of(DUP));
            rcDes.add(new InvokeInsnNode(INVOKESPECIAL, className, "<init>", "()V"));
        } catch (NoSuchMethodException e) {
            if ((flag & LENIENT) == 0) throw new IllegalArgumentException("????????????????????? " + owner.getName());
            else {
//                InsnList err = (m0.code = new AttrCode(m0)).instructions;
//                err.clear();
//                m0.code.localSize = 2; m0.code.stackSize = 3;
//
//                err.add(new ClassInsnNode(NEW, "java/lang/AssertionError"));
//                err.add(NPInsnNode.of(DUP));
//                err.add(new LdcInsnNode(new CstString(owner.getName() + " ?????????????????????")));
//                err.add(new InvokeInsnNode(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/String;)V"));
//                err.add(NPInsnNode.of(ATHROW));
            }
        }

        rcDes.add(NPInsnNode.of(ASTORE_2));

        ToIntMap<Type> storedSer = new ToIntMap<>(4);
        for (Field field : (flag & NOINHERIT) == 0 ?
                        Arrays.asList(owner.getDeclaredFields()) :
                        ReflectionUtils.getFields(owner)) {
            if ((field.getModifiers() & (AccessFlag.TRANSIENT_OR_VARARGS | AccessFlag.STATIC)) != 0) continue;

            FieldInsnNode getFA;
            if ((field.getModifiers() & AccessFlag.FINAL) != 0) {
                roj.asm.tree.Field acc = new roj.asm.tree.Field(AccessFlag.STATIC, "a" + cz.fields.size(), new Type("roj/reflect/FieldAccessor"));
                cz.fields.add(acc);
                int accId;

                InsnList init = cz.methods.get(2).code.instructions;
                InsnNode ret = init.remove(init.size() - 1);
                init.add(new LdcInsnNode(new CstClass(className)));
                init.add(new LdcInsnNode(new CstString(field.getName())));
                init.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/serial/GenSer", "acc", "(Ljava/lang/Class;Ljava/lang/String;)Lroj/reflect/FieldAccessor;"));
                init.add(new FieldInsnNode(PUTSTATIC, cz, accId = cz.fields.size() - 1));
                init.add(ret);
                getFA = new FieldInsnNode(GETSTATIC, cz, accId);
            } else getFA = null;

            Type type = ParamHelper.parseField(ParamHelper.class2asm(field.getType()));

            // ser: dup, ldc [name], aload_2, getfield, convert, put
            rcSer.add(NPInsnNode.of(DUP));
            rcSer.add(new LdcInsnNode(new CstString(field.getName())));
            rcSer.add(NPInsnNode.of(ALOAD_2));
            rcSer.add(new FieldInsnNode(GETFIELD, className, field.getName(), type));

            // rcDes: dup, aload_1, ldc [name], get, convert, putfield
            if (getFA != null) {
                rcDes.add(getFA);
                rcDes.add(NPInsnNode.of(ALOAD_2));
                rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/reflect/FieldAccessor", "setInstance", "(Ljava/lang/Object;)V"));
                rcDes.add(getFA);
            } else {
                rcDes.add(NPInsnNode.of(ALOAD_2));
            }
            rcDes.add(NPInsnNode.of(ALOAD_1));
            rcDes.add(new LdcInsnNode(new CstString(field.getName())));
            rcDes.add(new InvokeItfInsnNode("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"));
            rcDes.add(new ClassInsnNode(CHECKCAST, "roj/config/data/CEntry"));

            // convert jfield -> cobject
            a:
            {
                if (type.array == 0) {
                    switch (type.type) {
                        case Type.BOOLEAN:
                            rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CBoolean", "valueOf", "(Z)Lroj/config/data/CEntry;"));
                            rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "asBool", "()Z"));
                            break a;
                        case Type.BYTE:
                        case Type.CHAR:
                        case Type.SHORT:
                        case Type.INT:
                            rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CInteger", "valueOf", "(I)Lroj/config/data/CInteger;"));
                            rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "asInteger", "()I"));
                            switch (type.type) {
                                case Type.BYTE:
                                    rcDes.add(NPInsnNode.of(I2B));
                                    break;
                                case Type.CHAR:
                                    rcDes.add(NPInsnNode.of(I2C));
                                    break;
                                case Type.SHORT:
                                    rcDes.add(NPInsnNode.of(I2S));
                                    break;
                            }
                            break a;
                        case Type.FLOAT:
                            rcSer.add(NPInsnNode.of(F2D));
                            rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CDouble", "valueOf", "(D)Lroj/config/data/CDouble;"));
                            rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "asDouble", "()D"));
                            rcDes.add(NPInsnNode.of(D2F));
                            break a;
                        case Type.DOUBLE:
                            rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CDouble", "valueOf", "(D)Lroj/config/data/CDouble;"));
                            rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "asDouble", "()D"));
                            break a;
                        case Type.LONG:
                            rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CLong", "valueOf", "(J)Lroj/config/data/CLong;"));
                            rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "asLong", "()J"));
                            break a;
                    }
                }
                if (type.array == 1 && type.owner == null) {
                    rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/serial/Serializers", "wArray", "([" + (char) type.type + ")Lroj/config/data/CEntry;"));
                    rcDes.set(rcDes.size() - 1, new ClassInsnNode(CHECKCAST, "roj/config/data/CList"));
                    rcDes.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/serial/Serializers", "rArray" + (char) type.type, "(Lroj/config/data/CList;)[" + (char) type.type));
                } else {
                    findBestSerializer(0x02000000 | flag, storedSer, cz, rcSer, rcDes, type);
                }
            }

            rcSer.add(new InvokeItfInsnNode("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
            rcSer.add(NPInsnNode.of(POP));

            if (getFA == null) {
                rcDes.add(new FieldInsnNode(PUTFIELD, className, field.getName(), type));
            } else {
                StringBuilder pf = new StringBuilder().append("set");
                switch (type.type) {
                    case ARRAY:
                    case CLASS:
                        pf.append("Object");
                        break;
                    default:
                        pf.append(Type.toString(type.type));
                        pf.setCharAt(0, Character.toUpperCase(pf.charAt(0)));
                }
                StringBuilder tp = new StringBuilder().append("(");
                if (type.owner == null && type.array == 0) {
                    tp.append((char)type.type);
                } else {
                    tp.append("Ljava/lang/Object;");
                }
                rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/reflect/FieldAccessor",
                                             pf.toString(),
                                             tp.append(")V").toString()));
                rcDes.add(getFA);
                rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/reflect/FieldAccessor",  "clearInstance", "()V"));
            }
        }
        rcSer.add(NPInsnNode.of(RETURN));
        rcDes.add(NPInsnNode.of(ALOAD_2));
        rcDes.add(NPInsnNode.of(ARETURN));

        if (cz.methods.get(2).code.instructions.size() == 1) {
            cz.methods.remove(2);
            cz.interfaces.remove(cz.interfaces.size() - 1);
        }

        return (Serializer<?>) DirectAccessor.i_build(cz);
    }

    private void findBestSerializer(int flag, ToIntMap<Type> storedSer, Clazz cz, InsnList rcSer, InsnList rcDes, Type type) {
        if (type.array == 0) {
            switch (type.owner) {
                case "java/lang/String":
                case "java/lang/CharSequence":
                    rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CString", "valueOf", "(Ljava/lang/String;)Lroj/config/data/CString;"));
                    rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "asString", "()Ljava/lang/String;"));
                    return;
                // Primitive wrappers may be null, using Specified Serializer is simpler
            }
        }
        // rest: 'standard' object
        Class<?> cType;
        try {
            cType = type.toJavaClass();
            register(cType, flag & 0x00FFFFFF); // ????????????
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("A necessary class can not be found: " + type, e);
        }

        boolean st = cType != Object.class && (
                (flag & NONSTATIC) == 0 || type.array > 0 ||
                // Not inheritable
                (cType.getModifiers() & (AccessFlag.ANNOTATION | AccessFlag.FINAL | AccessFlag.ENUM)) != 0);
        if (st) {
            int id = storedSer.getOrDefault(type, -1);
            if (id == -1) {
                id = cz.fields.size();
                storedSer.putInt(type, id);

                roj.asm.tree.Field field1 = new roj.asm.tree.Field(AccessFlag.STATIC, "s" + id, new Type("roj/config/serial/Serializer"));
                cz.fields.add(field1);

                // init(), ????????????????????????
                InsnList init = cz.methods.get(2).code.instructions;
                InsnNode ret = init.remove(init.size() - 1);
                init.add(NPInsnNode.of(ALOAD_1));
                init.add(new LdcInsnNode(new CstString(cType.getName())));
                init.add(new InvokeInsnNode(INVOKESPECIAL, "roj/config/serial/Serializers", "find", "(Ljava/lang/String;)Lroj/config/serial/Serializer;"));
                init.add(new FieldInsnNode(PUTSTATIC, cz, id));
                init.add(ret);
            }
            FieldInsnNode GET = new FieldInsnNode(GETSTATIC, cz, id);
            rcSer.add(rcSer.size() - ((flag >> 24) & 0xFF), GET);
            rcSer.add(new InvokeItfInsnNode("roj/config/serial/Serializer", "serializeRc", "(Ljava/lang/Object;)Lroj/config/data/CEntry;"));

            rcDes.add(rcDes.size() - 4, GET);
            rcDes.add(new InvokeItfInsnNode("roj/config/serial/Serializer", "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;"));
        } else {
            rcSer.add(new InvokeInsnNode(INVOKESTATIC, "roj/config/data/CEntry", "wrap", "(Ljava/lang/Object;)Lroj/config/data/CEntry;"));
            rcDes.add(new InvokeInsnNode(INVOKEVIRTUAL, "roj/config/data/CEntry", "unwrap", "()Ljava/lang/Object;"));
        }

        rcDes.add(new ClassInsnNode(CHECKCAST, type.owner));
    }

    private Serializer<?> arraySerializer(Class<?> owner, int flag) {
        Clazz cz = new Clazz();
        DirectAccessor.makeHeader("roj/config/serial/GenArraySer$" + ordinal.getAndIncrement(),
                                  "roj/config/serial/Serializer",
                                  cz);
        cz.version = 51 << 16;
        cz.interfaces.add("roj/config/serial/Serializers$Init");
        cz.parent = "roj/config/serial/GenArraySer";
        DirectAccessor.addInit(cz);

        Method m0 = new Method(PUBLIC, cz, "init", "(Lroj/config/serial/Serializers;)V");
        cz.methods.add(m0);
        AttrCode c0 = m0.code = new AttrCode(m0);
        c0.interpretFlags = AttrCode.COMPUTE_SIZES;
        c0.instructions.add(NPInsnNode.of(RETURN));

        m0 = new Method(PUBLIC, cz,
                               "serialize0", "(Ljava/lang/Object;)Lroj/config/data/CList;");
        cz.methods.add(m0);
        c0 = m0.code = new AttrCode(m0);
        c0.stackSize = c0.localSize = 4;
        InsnList rcSer = c0.instructions;


        m0 = new Method(PUBLIC, cz, "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;");
        cz.methods.add(m0);
        c0 = m0.code = new AttrCode(m0);
        c0.stackSize = c0.localSize = 4;
        InsnList rcDes = c0.instructions;

        // ????????? SER

        rcSer.add(new ClassInsnNode(NEW, "java/util/ArrayList"));
        rcSer.add(NPInsnNode.of(DUP));

        rcSer.add(NPInsnNode.of(ALOAD_1));
        rcSer.add(new ClassInsnNode(CHECKCAST, owner.getName().replace('.', '/')));
        rcSer.add(NPInsnNode.of(DUP));
        rcSer.add(NPInsnNode.of(ASTORE_2));
        rcSer.add(NPInsnNode.of(ARRAYLENGTH));

        rcSer.add(new InvokeInsnNode(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V"));
        rcSer.add(NPInsnNode.of(ASTORE_1));

        rcSer.add(NPInsnNode.of(ICONST_0)); // int i = 0;
        rcSer.add(NPInsnNode.of(ISTORE_3));

        // ????????? DES

        rcDes.add(NPInsnNode.of(ALOAD_1));
        rcDes.add(new ClassInsnNode(CHECKCAST, "roj/config/data/CList"));
        rcDes.add(new FieldInsnNode(GETFIELD, "roj/config/data/CList", "list", new Type("java/util/List")));
        rcDes.add(NPInsnNode.of(DUP));
        rcDes.add(NPInsnNode.of(ASTORE_1));

        rcDes.add(new InvokeItfInsnNode("java/util/List", "size", "()I"));
        rcDes.add(new ClassInsnNode(ANEWARRAY, ParamHelper.class2asm(owner)));
        rcDes.add(NPInsnNode.of(ASTORE_2));

        rcDes.add(NPInsnNode.of(ICONST_0)); // int i = 0;
        rcDes.add(NPInsnNode.of(ISTORE_3));

        // ?????????
        InsnNode add = new InvokeItfInsnNode("java/util/List", "add", "(Ljava/lang/Object;)Z");
        InsnNode get = new InvokeItfInsnNode("java/util/List", "get", "(I)Ljava/lang/Object;");

        LabelInsnNode cycleBegin = new LabelInsnNode();
        LabelInsnNode cycleExit = new LabelInsnNode();
        rcSer.add(cycleBegin);
        InsnNode cycleBegin2 = new LabelInsnNode();
        InsnNode cycleExit2 = new LabelInsnNode();
        rcDes.add(cycleBegin2);

        // if (i < array.length) {
        rcSer.add(new NPInsnNode(ILOAD_3));
        rcSer.add(NPInsnNode.of(ALOAD_2));
        rcSer.add(NPInsnNode.of(ARRAYLENGTH));
        rcSer.add(new IfInsnNode(IF_icmpge, cycleExit));

        rcDes.add(new NPInsnNode(ILOAD_3));
        rcDes.add(NPInsnNode.of(ALOAD_2));
        rcDes.add(NPInsnNode.of(ARRAYLENGTH));
        rcDes.add(new IfInsnNode(IF_icmpge, cycleExit2));

        // local -> list, array, i

        // list.add(obj[i]);
        rcSer.add(NPInsnNode.of(ALOAD_1));
        rcSer.add(NPInsnNode.of(ALOAD_2));
        rcSer.add(NPInsnNode.of(ILOAD_3));
        rcSer.add(NPInsnNode.of(AALOAD));

        // obj[i] = list.get(i);
        rcDes.add(NPInsnNode.of(ALOAD_2));
        rcDes.add(NPInsnNode.of(ILOAD_3));

        rcDes.add(NPInsnNode.of(ALOAD_1));
        rcDes.add(NPInsnNode.of(ILOAD_3));
        rcDes.add(get);

        Class<?> upper = owner.getComponentType();
        findBestSerializer(0x03000000 | flag, new ToIntMap<>(), cz, rcSer, rcDes,
                           new Type(upper.getName().replace('.', '/')));

        rcSer.add(add);
        rcSer.add(NPInsnNode.of(POP));
        rcDes.add(NPInsnNode.of(AASTORE));

        // i++;
        rcSer.add(new IncrInsnNode(3, 1));
        rcDes.add(new IncrInsnNode(3, 1));

        // ???????????? SER

        rcSer.add(new GotoInsnNode(cycleBegin));
        rcSer.add(cycleExit);

        rcSer.add(new ClassInsnNode(NEW, "roj/config/data/CList"));
        rcSer.add(NPInsnNode.of(DUP));
        rcSer.add(NPInsnNode.of(ALOAD_1));
        rcSer.add(new InvokeInsnNode(INVOKESPECIAL, "roj/config/data/CList", "<init>", "(Ljava/util/List;)V"));
        rcSer.add(NPInsnNode.of(ARETURN));

        // ???????????? DES

        rcDes.add(new GotoInsnNode(cycleBegin2));
        rcDes.add(cycleExit2);
        rcDes.add(NPInsnNode.of(ALOAD_2));
        rcDes.add(NPInsnNode.of(ARETURN));

        if (cz.methods.get(2).code.instructions.size() == 1) {
            cz.methods.remove(2);
            cz.interfaces.remove(cz.interfaces.size() - 1);
        }

        return (Serializer<?>) DirectAccessor.i_build(cz);
    }

    public static CEntry wArray(int[] arr) {
        CList dst = new CList(arr.length);
        for (int o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(short[] arr) {
        CList dst = new CList(arr.length);
        for (int o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(char[] arr) {
        CList dst = new CList(arr.length);
        for (int o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(byte[] arr) {
        CList dst = new CList(arr.length);
        for (int o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(boolean[] arr) {
        CList dst = new CList(arr.length);
        for (boolean o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(double[] arr) {
        CList dst = new CList(arr.length);
        for (double o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(float[] arr) {
        CList dst = new CList(arr.length);
        for (double o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static CEntry wArray(long[] arr) {
        CList dst = new CList(arr.length);
        for (double o1 : arr) {
            dst.add(o1);
        }
        return dst;
    }

    public static int[] rArrayI(CList list) {
        int[] arr = new int[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = raw.get(i).asInteger();
        }
        return arr;
    }

    public static boolean[] rArrayZ(CList list) {
        boolean[] arr = new boolean[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = raw.get(i).asBool();
        }
        return arr;
    }

    public static short[] rArrayS(CList list) {
        short[] arr = new short[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = (short) raw.get(i).asInteger();
        }
        return arr;
    }

    public static char[] rArrayC(CList list) {
        char[] arr = new char[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = (char) raw.get(i).asInteger();
        }
        return arr;
    }

    public static byte[] rArrayB(CList list) {
        byte[] arr = new byte[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = (byte) raw.get(i).asInteger();
        }
        return arr;
    }

    public static long[] rArrayJ(CList list) {
        long[] arr = new long[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = raw.get(i).asLong();
        }
        return arr;
    }

    public static float[] rArrayF(CList list) {
        float[] arr = new float[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = (float) raw.get(i).asDouble();
        }
        return arr;
    }

    public static double[] rArrayD(CList list) {
        double[] arr = new double[list.size()];
        List<CEntry> raw = list.raw();
        for (int i = 0; i < raw.size(); i++) {
            arr[i] = raw.get(i).asDouble();
        }
        return arr;
    }
}