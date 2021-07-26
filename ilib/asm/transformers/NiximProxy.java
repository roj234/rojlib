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

package ilib.asm.transformers;

import ilib.api.IFasterClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstType;
import roj.asm.cst.CstUTF;
import roj.asm.nixim.NiximTransformer;
import roj.asm.tree.Method;
import roj.asm.tree.insn.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.reflect.DirectMethodAccess;
import roj.reflect.Instanced;
import roj.util.ByteList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class NiximProxy extends NiximTransformer implements IFasterClassTransformer {
    public interface FuckForgeRemapper extends Instanced {
        Map<String, String> mmp(String className);
    }

    public static final NiximProxy instance = new NiximProxy();
    public static boolean alreadyAtDeobfEnv, checkedReally;

    public NiximProxy() {

    }

    static final FuckForgeRemapper MAP_GETTER;

    static {
        MAP_GETTER = DirectMethodAccess.get(FuckForgeRemapper.class, "mmp", FMLDeobfuscatingRemapper.INSTANCE, "getMethodMap");
    }

    @Override
    public ByteList transform(String name, String transformedName, ByteList basicClass) {
        if (transformedName == null) {
            return basicClass;
            //realClassName = Parser.classData(bytes).get(0);
        }

        List<NiximData> niximDatas = classRemapping.remove(transformedName);

        if (niximDatas == null)
            return basicClass;

        if (true /*alreadyAtDeobfEnv && reallyAtDeobfEnv(basicClass, transformedName)*/) {
            return nixim(transformedName, basicClass, niximDatas);
        }

        String clazzName = Parser.simpleData(basicClass.list).get(0);

        for (NiximData niximData : niximDatas) {

            Map<String, Method> methodMapping = niximData.methodMap;
            Map<String, Method> methodMapping2 = new HashMap<>(methodMapping);
            methodMapping.clear();

            Map<String, String> methodRename = niximData.methodRename;
            Map<String, String> replaceMapping = new HashMap<>(methodRename);
            methodRename.clear();

            //logger.info("PreNiximForge(mcp -> srg) " + data.clazzName);

            Map<String, String> methodMap = MAP_GETTER.mmp(clazzName);
            Map<String, String> flipped = methodMap == null ? Collections.emptyMap() : new MyHashMap<>(methodMap.size());
            if (methodMap != null) {
                for (Map.Entry<String, String> entry : methodMap.entrySet()) {
                    flipped.put(entry.getValue(), entry.getKey());
                }
            }

            for (Map.Entry<String, Method> e : methodMapping2.entrySet()) {
                unmapMethod(e.getValue());
                methodMapping.put(unmapNiximDesc(flipped, e.getKey()), e.getValue());
            }

            for (Map.Entry<String, String> e : replaceMapping.entrySet()) {
                methodRename.put(unmapNiximDesc(flipped, e.getKey()), e.getValue());
            }
        }

        return nixim(transformedName, basicClass, niximDatas);
    }

    private boolean reallyAtDeobfEnv(byte[] basicClass, String transformedName) {
        if (checkedReally) return true;

        List<NiximData> niximDatas = classRemapping.get(transformedName);

        if (niximDatas == null)
            return true;
        try {
            String clazzName = Parser.simpleData(basicClass).get(0);
            if (transformedName.replace('.', '/').equals(clazzName)) {
                return checkedReally = true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static String unmapNiximDesc(Map<String, String> methodMap, String name) {
        final String name1 = name;

        int index = name.indexOf("|");
        if (index == -1) {
            name = FMLDeobfuscatingRemapper.INSTANCE.unmap(name);
        } else {
            String b = name.substring(index + 1);
            if (b.length() > "()V".length()) {
                List<Type> types = ParamHelper.parseMethod(name.substring(index + 1));
                for (Type type : types) {
                    if (type.owner != null)
                        type.owner = FMLDeobfuscatingRemapper.INSTANCE.unmap(type.owner);
                }
                b = ParamHelper.getMethod(types);
            }
            String a = name.substring(0, index);
            if (methodMap.containsKey(a)) {
                a = methodMap.get(a);
                a = a.substring(0, a.indexOf('('));
            }
            name = a + '|' + b;
            //if(b.length() < 3)
            //    return name;
        }
        //println("[~] Unmap Name " + name1 + " -> " + name);
        return name;
    }

    @SuppressWarnings("fallthrough")
    public static void unmapMethod(Method method) {
        {
            List<Type> types = method.parameters();
            for (Type type : types) {
                if (type.owner != null)
                    type.owner = FMLDeobfuscatingRemapper.INSTANCE.unmap(type.owner);
            }
            Type type = method.getReturnType();
            if (type.owner != null)
                type.owner = FMLDeobfuscatingRemapper.INSTANCE.unmap(type.owner);
            method.resetParam(true);
        }
        //println("[~] Unmap Method " + method.name);

        if (method.code != null) {
            for (InsnNode node : method.code.instructions) {
                switch (node.getOpcode()) {
                    case GETSTATIC:
                    case PUTSTATIC:
                    case PUTFIELD:
                    case GETFIELD: {
                        FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
                        if (fieldInsnNode.type.owner != null)
                            fieldInsnNode.type.owner = FMLDeobfuscatingRemapper.INSTANCE.unmap(fieldInsnNode.type.owner);
                        //println("[~] Unmap Field " + node);
                        break;
                    }
                    case INVOKESTATIC:
                    case INVOKEDYNAMIC:
                    case INVOKESPECIAL:
                    case INVOKEINTERFACE:
                    case INVOKEVIRTUAL: {
                        IInvocationInsnNode invocationInsnNode = (IInvocationInsnNode) node;
                        List<Type> types = invocationInsnNode.parameters();
                        for (Type type : types) {
                            if (type.owner != null)
                                type.owner = FMLDeobfuscatingRemapper.INSTANCE.unmap(type.owner);
                        }
                        Type type = invocationInsnNode.returnType();
                        if (type.owner != null)
                            type.owner = FMLDeobfuscatingRemapper.INSTANCE.unmap(type.owner);
                        //println("[~] Unmap Method " + node);

                        if (node.getOpcode() == Opcodes.INVOKEDYNAMIC)
                            break;

                    }

                    case NEW:
                    case ANEWARRAY:
                    case INSTANCEOF:
                    case CHECKCAST: {
                        IClassInsnNode classInsnNode = (IClassInsnNode) node;
                        classInsnNode.owner(FMLDeobfuscatingRemapper.INSTANCE.unmap(classInsnNode.owner()));
                        //println("[~] Unmap Clazz " + node);
                        break;
                    }

                    case LDC:
                    case LDC_W:
                    case LDC2_W: {
                        LoadConstInsnNode loadConstInsnNode = (LoadConstInsnNode) node;
                        if (loadConstInsnNode.c.type() == CstType.CLASS) {
                            CstClass clazz = (CstClass) loadConstInsnNode.c;
                            clazz.setValue(new CstUTF(FMLDeobfuscatingRemapper.INSTANCE.unmap(clazz.getValue().getString())));
                            logger.info("[~] Unmap Const " + node);
                        }
                        break;
                    }
                }
            }
        }
    }
}