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
package ilib;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import ilib.asm.Loader;
import ilib.util.MyImmutableMultimap;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.asm.transformers.AccessTransformer;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrInnerClasses;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.reflect.DirectFieldAccess;
import roj.reflect.DirectFieldAccessor;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;

@Deprecated
public class AT_PATCH_AT implements IClassTransformer {
    private static final boolean DEBUG = false;

    private final Multimap<String, Modifier> modifiers;

    public static class Modifier {
        public String name = "";

        public String desc = "";

        public int oldAccess = 0;

        public int newAccess = 0;

        public int targetAccess = 0;

        public boolean changeFinal = false;

        public boolean markFinal = false;

        protected boolean modifyClassVisibility;

        private void setTargetAccess(String name) {
        }
    }

    public static DirectFieldAccessor DFA;

    static {
        try {
            DFA = DirectFieldAccess.get(AccessTransformer.class, AccessTransformer.class.getDeclaredField("modifiers"));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public AT_PATCH_AT(String description, AccessTransformer accessTransformer) {
        DFA.setInstance(accessTransformer);
        this.modifiers = Helpers.cast(new MyImmutableMultimap((Multimap<String, Object>) DFA.getObject()));
        DFA.clearInstance();
        Loader.logger().info(modifiers.size() + " entries fixed");
        this.desc = description;
    }

    String desc;

    @Override
    public String toString() {
        return getClass().getName() + '[' + desc + ']';
    }

    public AT_PATCH_AT(Class<? extends AT_PATCH_AT> dummyClazz) {
        this.modifiers = ArrayListMultimap.create();
    }


    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null)
            return null;
        if (!this.modifiers.containsKey(transformedName))
            return bytes;
        if (DEBUG)
            FMLLog.log.debug("Considering all methods and fields on {} ({})", transformedName, name);
        ConstantData data = Parser.parseConstants(bytes, true);
        Collection<Modifier> mods = this.modifiers.get(transformedName);
        for (Modifier m : mods) {
            if (m.modifyClassVisibility) {
                data.accesses.flag = getFixedAccess(data.accesses.flag, m);
                if (DEBUG)
                    FMLLog.log.debug("Class: {} {} -> {}", name, toBinary(m.oldAccess), toBinary(m.newAccess));
                Attribute aClass = data.attrByName("InnerClasses");
                if (aClass != null) {
                    AttrInnerClasses attributeInnerClass = new AttrInnerClasses(new ByteReader(aClass.getRawData()), data.cp);
                    for (AttrInnerClasses.InnerClass innerClass : attributeInnerClass.classes) {
                        if (innerClass.name.equals(data.name))
                            innerClass.flags = getFixedAccess(innerClass.flags, m);
                    }
                    data.attributes.remove(aClass);
                    data.attributes.add(attributeInnerClass);
                }
            }
            if (m.desc.isEmpty()) {
                for (FieldSimple n : data.fields) {
                    if (n.name.getString().equals(m.name) || m.name.equals("*")) {
                        n.accesses.flag = getFixedAccess(n.accesses.flag, m);
                        if (DEBUG)
                            FMLLog.log.debug("Field: {}.{} {} -> {}", name, n.name.getString(), toBinary(m.oldAccess), toBinary(m.newAccess));
                        if (!m.name.equals("*"))
                            break;
                    }
                }
                continue;
            }
            List<MethodSimple> nowOverrideable = Lists.newArrayList();
            for (MethodSimple n : data.methods) {
                if ((n.name.getString().equals(m.name) && n.type.getString().equals(m.desc)) || m.name.equals("*")) {
                    n.accesses.flag = getFixedAccess(n.accesses.flag, m);
                    if (!n.name.getString().equals("<init>")) {
                        boolean wasPrivate = ((m.oldAccess & 0x2) == 2);
                        boolean isNowPrivate = ((m.newAccess & 0x2) == 2);
                        if (wasPrivate && !isNowPrivate)
                            nowOverrideable.add(n);
                    }
                    if (DEBUG)
                        FMLLog.log.debug("Method: {}.{}{} {} -> {}", name, n.name.getString(), n.type.getString(), toBinary(m.oldAccess), toBinary(m.newAccess));
                    if (!m.name.equals("*"))
                        break;
                }
            }
            if (!nowOverrideable.isEmpty())
                replaceInvokeSpecial(data, nowOverrideable);
        }
        return Parser.toByteArray(data, true);
    }

    private void replaceInvokeSpecial(ConstantData clazz, List<MethodSimple> toReplace) {
        for (MethodSimple method : clazz.methods) {
            AttrCode code = Parser.getOrCreateCode(clazz, method);
            if (code == null) {
                System.out.println("InvokeSpecial");
                return;
            }
            for (InsnNode insn : code.instructions) {
                if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                    InvokeInsnNode mInsn = (InvokeInsnNode) insn;
                    for (MethodSimple n : toReplace) {
                        if (n.name.getString().equals(mInsn.name) && n.type.getString().equals(mInsn.rawTypes())) {
                            insn.code = Opcodes.INVOKEVIRTUAL;
                            break;
                        }
                    }
                }
            }
        }
    }

    private static String toBinary(int num) {
        return String.format("%16s", Integer.toBinaryString(num)).replace(' ', '0');
    }

    private static short getFixedAccess(int access, Modifier target) {
        target.oldAccess = access;
        int t = target.targetAccess;
        int ret = access & 0xFFFFFFF8;
        switch (access & 0x7) {
            case 2:
                ret |= t;
                break;
            case 0:
                ret |= ((t != 2) ? t : 0);
                break;
            case 4:
                ret |= ((t != 2 && t != 0) ? t : 4);
                break;
            case 1:
                ret |= ((t != 2 && t != 0 && t != 4) ? t : 1);
                break;
            default:
                throw new RuntimeException("The fuck?");
        }
        if (target.changeFinal)
            if (target.markFinal) {
                ret |= 0x10;
            } else {
                ret &= 0xFFFFFFEF;
            }
        target.newAccess = ret;
        return (short) ret;
    }
}
