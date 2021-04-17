/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: MIAccessTransformer.java
 */
package roj.asm.nixim;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.SharedCache;
import roj.asm.constant.*;
import roj.asm.struct.ConstantData;
import roj.asm.struct.Field;
import roj.asm.struct.Method;
import roj.asm.struct.anno.AnnValArray;
import roj.asm.struct.attr.*;
import roj.asm.struct.insn.FieldInsnNode;
import roj.asm.struct.insn.InsnNode;
import roj.asm.struct.insn.InvocationInsnNode;
import roj.asm.struct.insn.InvokeDynamicInsnNode;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.IConstantSerializable;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.asm.util.InsnList;
import roj.asm.util.frame.Frame;
import roj.asm.util.frame.Var;
import roj.asm.util.type.*;
import roj.collect.*;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;
import roj.util.log.ILogger;
import roj.util.log.LogManager;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static roj.asm.Opcodes.*;

public class NiximTransformer {
    protected static final Map<String, List<NiximData>> classRemapping = new MyHashMap<>();

    public static final String REMAP_CLASS_TYPE = ParamHelper.classDescriptor(Nixim.class);
    public static final String REMAP_METHOD_TYPE = ParamHelper.classDescriptor(RemapTo.class);
    public static final String IMPL_INTERFACE_TYPE = ParamHelper.classDescriptor(ImplInterface.class);
    public static final String SHADOW_TYPE = ParamHelper.classDescriptor(Shadow.class);
    public static final String COPY_TYPE = ParamHelper.classDescriptor(Copy.class);
    public static final String ANNO_TYPE = "RuntimeInvisibleAnnotations";

    protected static final ILogger logger = LogManager.getLogger("Nixim");

    public static boolean debug = false;

    public static ByteList nixim(String realClassName, ByteList bytes, List<NiximData> niximDatas) {
        ConstantData data;

        //Set<String> types = new MyHashSet<>();

        boolean first = true;

        for (NiximData niximData : niximDatas) {
            data = Parser.parseConstants(bytes, true);

            /*if (first) {
                for (MethodSimple method : data.methods) {
                    types.add(method.name.getString() + '|' + method.type.getString());
                }
                for (FieldSimple field : data.fields) {
                    types.add(field.name.getString() + '|' + field.type.getString());
                }
                first = false;
            }*/

            for (String s : niximData.appendInterfaces) {
                data.interfaces.add(data.writer.getClazz(s));
            }

            Map<String, Method> methodRemapper = niximData.methodMap;

            logger.info("NiximClass " + data.name);

            Int2IntMap i2i = new Int2IntMap();

            if (!niximData.bsm.isEmpty()) {
                AttrBootstrapMethods bootstrapMethods;
                Attribute attr = data.attrByName("BootstrapMethods");
                if (attr == null) {
                    data.attributes.add(bootstrapMethods = new AttrBootstrapMethods());

                    if (debug)
                        logger.info("Create BSM");
                } else {
                    bootstrapMethods = new AttrBootstrapMethods(new ByteReader(attr.getRawData()), data.constants);
                    final ListIterator<Attribute> li = data.attributes.listIterator();
                    while (li.hasNext()) {
                        if (li.next() == attr) {
                            li.set(bootstrapMethods);
                            break;
                        }
                    }
                }
                for (IntMap.Entry<LambdaInfo> entry : niximData.bsm.entrySet()) {
                    int newId = bootstrapMethods.methods.size();
                    LambdaInfo info = entry.getValue();
                    bootstrapMethods.methods.add(info.bootstrapMethod);

                    if (debug)
                        logger.info("Replace old BTIndex " + info.nodes.get(0).bootstrapTableIndex + " to " + newId);

                    for (InvokeDynamicInsnNode node : info.nodes) {
                        node.bootstrapTableIndex = newId;
                        if (debug)
                            logger.info("Affected node: " + node);
                    }
                }
            }

            ListIterator<IConstantSerializable> itr = Helpers.cast(data.methods.listIterator());
            List<IConstantSerializable> removed = new ArrayList<>();
            while (itr.hasNext()) {
                MethodSimple method = (MethodSimple) itr.next();

                String name = method.name.getString();

                Method replacement = methodRemapper.get(name);
                if (replacement != null) {
                    replacement.name = method.name.getString();
                    replacement.accesses = method.accesses.copy();
                    replacement.setDesc(method.type.getString());
                    replacement.resetParam(false);

                    itr.set(replacement);

                    IConstantSerializable serializable = method;

                    if ("<init>".equals(name)) {
                        serializable = fixInitMethod(niximData, data, replacement, method);
                    }

                    fixInvokeVirtualSelf(replacement, /*types, */niximData.originalClassName, data.name);

                    if (debug)
                        logger.info("NiximMethodIgnoreParam " + replacement.name);

                    methodRemapper.remove(method.name.getString());
                    removed.add(serializable);

                    continue;
                }

                String key = name + '|' + method.type.getString();

                replacement = methodRemapper.get(key);
                if (replacement != null) {
                    replacement.name = method.name.getString();
                    replacement.accesses = method.accesses.copy();

                    IConstantSerializable serializable = method;

                    if ("<init>".equals(name)) {
                        serializable = fixInitMethod(niximData, data, replacement, method);
                    }

                    final Object v = niximData.properties.get("MTR:" + key);
                    if (v != null) {
                        replacement = doInjectAt(data, method, replacement, (String) v);
                    }
                    itr.set(replacement);

                    fixInvokeVirtualSelf(replacement, /*types, */niximData.originalClassName, data.name);

                    if (debug)
                        logger.info("NiximMethod " + replacement.name);

                    methodRemapper.remove(key);
                    removed.add(serializable);
                }
            }

            if (!niximData.copyField.isEmpty()) {
                data.fields.addAll(Helpers.cast(niximData.copyField));
            }

            if (!niximData.copyMethod.isEmpty()) {
                data.methods.addAll(Helpers.cast(niximData.copyMethod));
            }

            if (!niximData.methodRename.isEmpty()) {
                Map<String, String> methodRename = niximData.methodRename;
                for (IConstantSerializable ms : removed) {
                    String newName = methodRename.remove(ms.name() + '|' + ms.rawDesc());
                    if (newName != null) {

                        if (debug)
                            logger.info("ReplaceEntryMatch: " + ms.name() + '(' + ms.rawDesc() + ") -> " + newName);
                        if (ms instanceof MethodSimple) {
                            ((MethodSimple) ms).name = data.writer.getUtf(newName);
                        } else {
                            ((Method) ms).name = newName;
                        }
                        List<IConstantSerializable> some = Helpers.cast(data.methods);
                        some.add(ms);
                    }
                }
            }

            if (!methodRemapper.isEmpty()) {
                final Map<String, Object> pr = niximData.properties;
                if (!pr.isEmpty()) {
                    List<IConstantSerializable> list = Helpers.cast(data.methods);
                    String sup = data.parent;

                    methodRemapper.keySet().removeIf(entry -> {
                        if (pr.containsKey("NHT:" + entry)) {
                            return true;
                        }
                        boolean flag = pr.containsKey("CPY:" + entry);
                        if (flag) {
                            list.add(fixSuperInvokeSpec(methodRemapper.get(entry), sup));
                        }
                        return flag;
                    });
                }
            }

            if (!methodRemapper.isEmpty()) {
                logger.error("======Methods======");
                itr = Helpers.cast(data.methods.listIterator());
                while (itr.hasNext()) {
                    IConstantSerializable method = itr.next();
                    logger.error(method.name() + " | " + method.rawDesc());
                }
                logger.error("======Nixim entries======");
                for (Map.Entry<String, Method> entry : methodRemapper.entrySet()) {
                    logger.error(entry.getKey() + " -> " + entry.getValue().name + '|' + entry.getValue().rawDesc());
                }
                throw new RuntimeException("NiximError: Nixim entries not hit!");
            }

            if (!niximData.methodRename.isEmpty()) {
                logger.error("======Methods======");
                itr = Helpers.cast(removed.listIterator());
                while (itr.hasNext()) {
                    IConstantSerializable method = itr.next();
                    logger.error(method.name() + " | " + method.rawDesc());
                }
                logger.error("======Nixim entries======");
                for (Map.Entry<String, String> entry : niximData.methodRename.entrySet()) {
                    logger.error(entry.getKey() + " -> " + entry.getValue());
                }
                throw new RuntimeException("NiximError: Rename entries not hit!");
            }

            try {
                data.verify();
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Fatal error during verifying " + data.name, e);
            }

            bytes = data.getBytes(SharedCache.bufCstPool(), SharedCache.bufGlobal());
        }

        if (debug) {
            try (FileOutputStream fos = new FileOutputStream(new File(realClassName.replace('/', '.') + ".class"))) {
                bytes.writeToStream(fos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return bytes;
    }

    private static Method fixSuperInvokeSpec(Method o, String superClz) {
        if (o.code == null)
            return o;
        for (InsnNode node : o.code.instructions) {
            if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                InvocationInsnNode node1 = (InvocationInsnNode) node;
                if (node1.owner() == null)
                    node1.owner(superClz);
            }
        }
        return o;
    }

    private static Method doInjectAt(ConstantData data, MethodSimple method, Method replacement, String bytePos) {
        List<String> arr = TextUtil.splitStringF(new ArrayList<>(2), bytePos, '|');
        int pos = Integer.parseInt(arr.get(0));
        byte opcode = Byte.parseByte(arr.get(1));
        int par2 = arr.size() > 2 ? Integer.parseInt(arr.get(2)) : -1;

        InsnList insertList = replacement.code.instructions;

        Method toReturn = new Method(data, method);
        InsnList currList = toReturn.code.instructions;

        IntBiMap<InsnNode> pcMap = currList.getPCMap();

        InsnNode node = pcMap.get(pos);
        if (node == null) {
            logger.warn("[" + data.name + "] Specified pos " + bytePos + " not found.");
            return toReturn;
        }
        if (node.getOpcode() == opcode) {
            insertList.remove(insertList.size() - 1);
            InsnNode invokeNode = insertList.get(insertList.size() - 2);
            if (invokeNode.getOpcode() == Opcodes.INVOKESTATIC) {
                InvocationInsnNode node1 = (InvocationInsnNode) invokeNode;
                if (node1.name().equals("<RETURN>")) {
                    insertList.remove(insertList.size() - 1);
                    insertList.remove(insertList.size() - 1);
                }
            }
            currList.addAll(currList.indexOf(node), insertList);
        } else {
            logger.warn("Specify node mismatch! " + node.getOpcode());
            return toReturn;
        }

        return toReturn;
    }

    private static void fixInvokeVirtualSelf(Method replacement, /*Set<String> types, */String slashSelfClass, String slashDestClass) {
        if (replacement.code != null) {
            for (InsnNode node : replacement.code.instructions) {
                if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    InvocationInsnNode node1 = (InvocationInsnNode) node;
                    if (node1.owner().equals(slashSelfClass)/* && types.contains(node1.getMethodName() + '|' + node1.getRawParam())*/) {
                        node1.owner(slashDestClass);
                    }
                }
            }
        }
    }

    private static IConstantSerializable fixInitMethod(NiximData niximData, ConstantData data, Method newInit, MethodSimple oldInit1) {
        if (niximData.methodRename.remove("<REPLACE_" + oldInit1.type.getString()) != null) {
            if (debug)
                logger.info("Fix <init> in replace mode.");

            InvocationInsnNode node1 = null;

            final InsnList list = newInit.code.instructions;

            for (InsnNode node : list) {
                if (node.code == Opcodes.INVOKESPECIAL) {
                    InvocationInsnNode invNode = (InvocationInsnNode) node;
                    if (invNode.owner().equals(data.name)) {
                        node1 = invNode;
                        break;
                    }
                }
            }

            if (node1 == null) {
                throw new IllegalArgumentException("没有找到InvokeSpecial");
            }

            if (debug)
                logger.info("Found invokeSpecialNode " + node1);

            while (list.get(1) != node1)
                list.remove(1);

            if (debug)
                logger.info("Set calling constructor to " + data.parent + ".<init>:()V");

            node1.owner(data.parent);
            node1.rawTypes("()V");

            //logger.info("替换的构造器： " + newInit);

            return newInit;
        }


        if (debug)
            logger.info("Fix <init> in inject at last mode.");

        Method oldInit = new Method(data, oldInit1);

        AttrCode oldCode = oldInit.code;
        AttrCode newCode = newInit.code;

        int firstId = newCode.localSize;

        newCode.localSize = Math.max(oldCode.localSize, newCode.localSize);

        for (Iterator<InsnNode> iterator = newCode.instructions.iterator(); iterator.hasNext(); ) {
            InsnNode node = iterator.next();
            iterator.remove();
            if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                InvocationInsnNode node1 = (InvocationInsnNode) node;
                if (node1.owner().equals(data.name) && node1.name().equals("<init>")) break;
                if (debug)
                    System.out.println(node1);
            }
        }

        oldCode.instructions.remove(oldCode.instructions.size() - 1);
        oldCode.instructions.remove(oldCode.instructions.size() - 1);

        newCode.instructions.addAll(0, oldCode.instructions);

        return newInit;
    }

    public static boolean read(@Nonnull final byte[] basicClass) {
        ConstantData data = Parser.parseConstants(basicClass, true);


        Object[] a = getClassArray(data, data.attrByName(ANNO_TYPE));

        if (a == null) {
            logger.warn("Not @Nixim annotation found in " + data.name);
            return false;
        }

        String destClass = (String) a[0];
        List<String> appendInterfaces = Helpers.cast(a[1]);

        String slashDestClass = destClass.replace('.', '/');
        String slashSelfClass = data.name.replace('.', '/');

        logger.info("NiximReadClass " + data.name + " => " + destClass);

        Map<String, Method> niximMethods = new MyHashMap<>();

        List<Method> copyMethod = new ArrayList<>();
        List<Field> copyField = new ArrayList<>();
        Map<String, String> copyMethodFind = new MyHashMap<>();
        Set<String> selfPSMethods = new MyHashSet<>();

        Map<String, String> methodRename = new MyHashMap<>();

        Map<String, String> fieldShadow = new MyHashMap<>();
        Map<String, String> methodShadow = new MyHashMap<>();

        Set<String> selfFields = new MyHashSet<>();

        for (FieldSimple field : data.fields) {
            String remapName = getAnnotationValue(data.constants, field.attrByName(ANNO_TYPE), SHADOW_TYPE);
            if (remapName != null) {
                String descriptor = field.type.getString();//ParamHelper.generalSingleDescriptor(field.type.getString());
                String oName = field.name.getString() + '|' + descriptor;
                fieldShadow.put(oName, remapName);
                if (debug)
                    logger.info("ShadowField " + descriptor + ':' + field.name.getString() + " -> " + remapName);
            } else if (hasAnnotation(data.constants, field.attrByName(ANNO_TYPE), COPY_TYPE)) {
                if (debug)
                    logger.info("CopyField " + field.name.getString());
                copyField.add(new Field(data, field));
                //copyMethodFind.add(field.name.getString() + '|' + field.type.getString());
            } else {
                if (!field.accesses.contains(AccessFlag.STATIC)) {
                    logger.warn("非static字段且没打@Copy / @Shadow, 可能会出错!");
                } else {
                    if (debug)
                        logger.info("Self field: " + field.name.getString() + '|' + field.type.getString());
                    selfFields.add(field.name.getString() + '|' + field.type.getString());
                }
            }
        }

        //println("slf " + selfFields.toString());
        Map<String, Object> properties = new MyHashMap<>();

        IntMap<LambdaInfo> lambdaBSM = new IntMap<>();

        Attribute attr = data.attrByName("BootstrapMethods");
        AttrBootstrapMethods bsm = attr == null ? null : new AttrBootstrapMethods(new ByteReader(attr.getRawData()), data.constants);

        List<MethodSimple> remapTmp = new ArrayList<>();

        for (MethodSimple method : data.methods) {
            String remapName = getAnnotationValue(data.constants, method.attrByName(ANNO_TYPE), SHADOW_TYPE);
            if (remapName != null) {
                String descriptor = method.type.getString();
                String oName = method.name.getString() + '|' + descriptor;
                methodShadow.put(oName, remapName);
                if (debug)
                    logger.info("ShadowMethod " + descriptor + ':' + method.name.getString() + " -> " + remapName);
            } else if (hasAnnotation(data.constants, method.attrByName(ANNO_TYPE), COPY_TYPE)) {
                if (debug)
                    logger.info("CopyMethod " + method.name.getString());
                Method method1;
                copyMethod.add(method1 = new Method(data, method));
                copyMethodFind.put(method.name.getString() + '|' + method.type.getString(), null);
                if (bsm != null) {
                    for (InsnNode node : method1.code.instructions) {
                        if (node.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                            InvokeDynamicInsnNode node1 = (InvokeDynamicInsnNode) node;
                            AttrBootstrapMethods.BootstrapMethod method2 = bsm.methods.get(node1.bootstrapTableIndex);
                            lambdaBSM.computeIfAbsent(node1.bootstrapTableIndex, () -> new LambdaInfo(method2)).nodes.add(node1);
                        }
                    }
                }

                replaceStackMap(slashSelfClass, slashDestClass, method1);
                replaceLVTandLVTT(slashSelfClass, slashDestClass, fieldShadow, method1);
            } else {
                remapTmp.add(method);
            }
        }

        for (MethodSimple method : remapTmp) {
            Data data1 = getAnnotationData(data.constants, method.attrByName(ANNO_TYPE), REMAP_METHOD_TYPE);
            if (data1 != null) {
                String remapName = data1.value;

                copyMethodFind.put(method.name.getString() + '|' + method.type.getString(), remapName);

                Method replaceMethod = new Method(data, method);
                replaceMethod.name = remapName;

                if (data1.ignoreParam) {
                    niximMethods.put(remapName, replaceMethod);
                    if (debug)
                        logger.info("[!]RemapIgnoreParamType [注意泛型桥接方法!!!]");
                    if (!data1.mustHit) {
                        // not must hit
                        properties.put("NHT:" + remapName, null);
                    }
                } else {
                    String key = remapName + '|' + method.type.getString();
                    niximMethods.put(key, replaceMethod);
                    if (data1.injectPos >= 0) {
                        logger.warn("WIP Function injectAt applied!!!");
                        properties.put("MTR:" + key, data1.injectPos + "|" + data1.codeAtPos);
                    }
                    if (!data1.mustHit) {
                        properties.put("NHT:" + key, null);
                    }
                    if (data1.isRemapCopy) {
                        properties.put("CPY:" + key, null);
                    }
                }

                if (replaceMethod.code == null) {
                    throw new RuntimeException("Source method is abstract " + replaceMethod.name());
                }

                replaceStackMap(slashSelfClass, slashDestClass, replaceMethod);

                replaceLVTandLVTT(slashSelfClass, slashDestClass, fieldShadow, replaceMethod);
                if (debug)
                    logger.info("RemapMethod " + method.type.getString() + ':' + method.name.getString() + " -> " + remapName);


                for (InsnNode node : replaceMethod.code.instructions) {
                    switch (node.code) {
                        case GETFIELD:
                        case GETSTATIC:
                        case PUTFIELD:
                        case PUTSTATIC: {
                            FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
                            if (fieldInsnNode.owner().equals(slashSelfClass)) {

                                String desc = fieldInsnNode.name + '|' + ParamHelper.getField(fieldInsnNode.type);

                                String fs = fieldShadow.get(desc);
                                if (fs != null) {
                                    if (debug)
                                        logger.info("FieldShadow[Code] " + fieldInsnNode.owner() + '.' + fieldInsnNode.name + " -> " + fs);
                                    fieldInsnNode.owner(slashDestClass);
                                    fieldInsnNode.name = fs;
                                } else if (!selfFields.contains(desc)) {
                                    fieldInsnNode.owner(slashDestClass);
                                }
                            }
                        }
                        break;
                        case INVOKESPECIAL: {
                            InvocationInsnNode invNode = (InvocationInsnNode) node;

                            if (debug)
                                logger.info("CallSuper[Code] " + invNode.owner() + '.' + invNode.name() + ':' + invNode.rawTypes() + " //////// " + slashDestClass);

                            if (invNode.owner().equals(slashDestClass)) {
                                if (data1.useSuperInject) {
                                    String mn = invNode.name();
                                    if (!mn.equals("<init>")) {
                                        if (debug)
                                            logger.info("SuperInject[Code] (" + node + ") " + slashDestClass + '.' + invNode.name() + ':' + invNode.rawTypes());

                                        if (!data1.isRemapCopy) {

                                            String newMethodName = mn + "_SIJ_" + System.currentTimeMillis();

                                            String originalDesc = mn + '|' + invNode.rawTypes();

                                            if (!methodRename.containsKey(originalDesc)) {
                                                methodRename.put(originalDesc, newMethodName);
                                            }

                                            invNode.name(newMethodName);
                                            invNode.setOpcode(Opcodes.INVOKEVIRTUAL);
                                        } else {
                                            invNode.owner(null);
                                        }
                                    }
                                } else if (invNode.name().equals("<init>")) {
                                    //throw new IllegalArgumentException("Unable to replace constructor due preventing NPE");
                                    methodRename.put("<REPLACE_" + invNode.rawTypes(), "");
                                }
                            }
                        }
                        break;
                        case INVOKEDYNAMIC:
                            InvokeDynamicInsnNode node1 = (InvokeDynamicInsnNode) node;
                            if (bsm == null)
                                throw new IllegalArgumentException("在没有BootstrapMethods的类中找到了InvokeDynamic!");
                            AttrBootstrapMethods.BootstrapMethod method1 = bsm.methods.get(node1.bootstrapTableIndex);
                            lambdaBSM.computeIfAbsent(node1.bootstrapTableIndex, () -> new LambdaInfo(method1)).nodes.add(node1);
                            break;
                    }
                }

            } else {
                if (!method.accesses.contains(AccessFlag.PUBLIC) || !method.accesses.contains(AccessFlag.STATIC)) {
                    if (!method.name.getString().equals("<init>") && !method.name.getString().startsWith("lambda$"))
                        logger.warn("非public static方法" + method.name.getString() + "且没打@Copy / @Shadow, 可能会出错!");
                    else {
                        if (debug)
                            logger.info("public static method " + method.name.getString());
                        selfPSMethods.add(method.name.getString() + '|' + method.type.getString());
                    }
                }
            }
        }

        for (Method replaceMethod : niximMethods.values()) {
            for (InsnNode node : replaceMethod.code.instructions) {
                switch (node.code) {
                    case INVOKESPECIAL:
                    case INVOKEINTERFACE:
                    case INVOKESTATIC:
                    case INVOKEVIRTUAL: {
                        InvocationInsnNode invNode = (InvocationInsnNode) node;
                        if (slashSelfClass.equals(invNode.owner())) {
                            String desc = invNode.name() + '|' + invNode.rawTypes();
                            String ms = methodShadow.get(desc);
                            if (ms != null) {
                                if (debug)
                                    logger.info("MethodShadow[Code] " + invNode.owner() + '.' + invNode.name() + " -> " + ms);
                                invNode.owner(slashDestClass);
                                invNode.name(ms);
                            } else if (copyMethodFind.containsKey(desc)) {
                                invNode.owner(slashDestClass);
                                if (debug)
                                    logger.info("MethodCopy[Code] " + invNode.owner() + '.' + invNode.name());

                                if ((ms = copyMethodFind.get(desc)) != null)
                                    invNode.name(ms);
                            } else if (!selfPSMethods.contains(desc)) {
                                invNode.owner(slashDestClass);
                                if (debug)
                                    logger.info("CompilerInherit[Code] " + invNode.owner() + '.' + desc);
                            }
                        }
                    }
                    break;
                }
            }
        }

        /**
         * Copy lambda method
         */
        for (LambdaInfo info : lambdaBSM.values()) {
            CstMethodHandle handle;

            for (InvokeDynamicInsnNode node : info.nodes) {
                for (Type type : node.parameters()) {
                    if (slashSelfClass.equals(type.owner))
                        type.owner = slashDestClass;
                }

                if (slashSelfClass.equals(node.returnType().owner))
                    node.returnType().owner = slashDestClass;

                if (debug)
                    logger.info("IDI: " + node);
            }

            for (Constant cst : info.bootstrapMethod.arguments) {
                if (cst.type == CstType.METHOD_HANDLE) {
                    handle = (CstMethodHandle) cst;
                    CstRef ref = handle.getRef();

                    boolean isSelfMethod = false;

                    if (ref.getClassName().equals(slashSelfClass)) {
                        isSelfMethod = true;
                        ref.setClazz(new CstClass(slashDestClass));
                    }

                    boolean found = false;

                    for (MethodSimple method : data.methods) {
                        if (method.name.equals(ref.desc().getName()) && method.type.equals(ref.desc().getType())) {
                            if (debug)
                                logger.info("Found lambda method " + method.name.getString());

                            Method method1 = new Method(data, method);

                            if (method1.code == null) {
                                throw new AbstractMethodError(method1.name());
                            }

                            replaceStackMap(slashSelfClass, slashDestClass, method1);

                            replaceLVTandLVTT(slashSelfClass, slashDestClass, fieldShadow, method1);

                            for (Type type : method1.parameters()) {
                                if (slashSelfClass.equals(type.owner))
                                    type.owner = slashDestClass;
                            }

                            method1.resetParam(true);

                            if (slashSelfClass.equals(method1.getReturnType().owner))
                                method1.getReturnType().owner = slashDestClass;

                            copyMethod.add(method1);
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        if (isSelfMethod) {
                            logger.error("NiximReadClass: Error: Lambda not found!");
                            logger.error("Method list below");
                            Iterator<IConstantSerializable> itr = Helpers.cast(data.methods.listIterator());
                            while (itr.hasNext()) {
                                IConstantSerializable method = itr.next();
                                logger.error(method.name() + "   |   " + method.rawDesc());
                            }
                            logger.error("Method list above");
                            throw new IllegalArgumentException("Not found lambda method with this descriptor " + ref.desc());
                        } else {
                            logger.info("NotSelf lambda: " + ref);
                        }
                    }

                    break;
                }
            }
        }

        /**
         * Process copy methods' due to annotation change
         */
        for (Method method : copyMethod) {
            for (InsnNode node : method.code.instructions) {
                switch (node.code) {
                    case GETFIELD:
                    case GETSTATIC:
                    case PUTFIELD:
                    case PUTSTATIC: {
                        FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
                        if (fieldInsnNode.owner().equals(slashSelfClass)) {

                            String desc = fieldInsnNode.name + '|' + ParamHelper.getField(fieldInsnNode.type);

                            String fs = fieldShadow.get(desc);
                            if (fs != null) {
                                if (debug)
                                    logger.info("FieldShadow[Code] " + fieldInsnNode.owner() + '.' + fieldInsnNode.name + " -> " + fs);
                                fieldInsnNode.owner(slashDestClass);
                                fieldInsnNode.name = fs;
                            } else if (!selfFields.contains(desc)) {
                                fieldInsnNode.owner(slashDestClass);
                            }
                        }
                        //println("Node:" + fieldInsnNode + " in " + method.getName());
                    }
                    break;
                    case INVOKESPECIAL:
                    case INVOKEINTERFACE:
                    case INVOKESTATIC:
                    case INVOKEVIRTUAL: {
                        InvocationInsnNode invNode = (InvocationInsnNode) node;
                        if (invNode.owner().equals(slashSelfClass)) {
                            String desc = invNode.name() + '|' + invNode.rawTypes();
                            String ms = methodShadow.get(desc);

                            if (ms != null) {
                                if (debug)
                                    logger.info("MethodShadow[Code] " + invNode.owner() + '.' + invNode.name() + " -> " + ms);
                                invNode.owner(slashDestClass);
                                invNode.name(ms);
                            } else if (copyMethodFind.containsKey(desc)) {
                                invNode.owner(slashDestClass);
                                if (debug)
                                    logger.info("CopyMethod[Code] " + invNode.owner() + '.' + desc);

                                if ((ms = copyMethodFind.get(desc)) != null)
                                    invNode.name(ms);
                            } else if (!selfPSMethods.contains(desc)) {
                                invNode.owner(slashDestClass);
                                if (debug)
                                    logger.info("CompilerInherit[Code] " + invNode.owner() + '.' + desc);
                            }
                        }
                    }
                    break;
                }
            }
        }

        if (niximMethods.size() > 0 || appendInterfaces.size() > 0 || copyMethod.size() > 0 || methodRename.size() > 0) {
            List<NiximData> dataList = classRemapping.computeIfAbsent(destClass, k -> new ArrayList<>());

            dataList.add(new NiximData(niximMethods, copyMethod, methodRename, copyField, lambdaBSM, slashSelfClass, properties, appendInterfaces));

            return true;
        } else {
            logger.warn("No nixim usage(s) found in " + data.name);
            return false;
        }
    }

    private static void replaceLVTandLVTT(String slashSelfClass, String slashDestClass, Map<String, String> fieldShadow, Method method) {
        AttrLocalVars lvt = method.code.getLVT();
        if (lvt != null) {
            for (LocalVariable entry : lvt.list) {
                Type type = (Type) entry.type;
                if (type.owner == null) continue;
                if (type.owner.equals(slashSelfClass)) {
                    //if(debug)
                    //    logger.info("Replace self " + entry);
                    type.owner = slashDestClass;
                }

                String fs = fieldShadow.get(entry.name + '|' + type.owner);
                if (fs != null) {
                    if (debug)
                        logger.info("FieldShadow[LVT] " + entry.name + " -> " + fs);
                    entry.name = fs;
                }
            }
        }

        lvt = method.code.getLVTT();
        if (lvt != null) {
            for (LocalVariable entry : lvt.list) {
                Signature signature = (Signature) entry.type;
                signature.rename((old) -> {
                    if (old.equals(slashSelfClass))
                        return slashDestClass;
                    return old;
                });

                String realTypeName = ((Generic) signature.returns).owner;

                String fs = fieldShadow.get(entry.name + '|' + realTypeName);
                if (fs != null) {
                    if (debug)
                        logger.info("FieldShadow[LVTT] " + entry.name + " -> " + fs);
                    entry.name = fs;
                }
            }
        }
    }

    private static void replaceStackMap(String slashSelfClass, String slashDestClass, Method method) {
        if (method.code.frames != null) {
            for (Frame frame : method.code.frames) {
                for (Var var : frame.locals) {
                    if (slashSelfClass.equals(var.owner)) {
                        var.owner = slashDestClass;
                        if (debug)
                            logger.info("Replaced StackMapEntry : " + var);
                    }
                }
                for (Var var : frame.stacks) {
                    if (slashSelfClass.equals(var.owner)) {
                        var.owner = slashDestClass;
                        if (debug)
                            logger.info("Replaced StackMapEntry : " + var);
                    }
                }
            }
        }
    }

    public static boolean removeByClass(String target, String source) {
        for (String target1 : classRemapping.keySet()) {
            if (target.equals(target1)) {
                List<NiximData> data = classRemapping.get(target1);
                for (Iterator<NiximData> itr = data.iterator(); itr.hasNext(); ) {
                    NiximData data1 = itr.next();
                    if (data1.originalClassName.equals(source)) {
                        itr.remove();
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    public static boolean removeByClass(String target) {
        for (Iterator<String> iterator = classRemapping.keySet().iterator(); iterator.hasNext(); ) {
            String s1 = iterator.next();
            if (target.equals(s1)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private static final class Data {
        int injectPos = -1;
        byte codeAtPos;
        String value;
        boolean ignoreParam = false, useSuperInject = true, mustHit = true, isRemapCopy = false, copyInterface = false;
    }


    private static Object[] getClassArray(ConstantData data, Attribute attribute) {
        if (attribute == null)
            return null;
        AttrAnnotation annotations = attribute instanceof AttrAnnotation ? (AttrAnnotation) attribute : new AttrAnnotation(false, new ByteReader(attribute.getRawData()), data.constants);

        List<String> list = null;
        String className = null;

        for (roj.asm.struct.anno.Annotation ann : annotations.annotations) {
            if (ann.rawDesc.equals(REMAP_CLASS_TYPE)) {
                if (ann.values != null) {
                    roj.asm.struct.anno.AnnVal annoValue = ann.values.get("value");
                    if (annoValue.type == roj.asm.struct.anno.AnnotationType.STRING) {
                        className = ((roj.asm.struct.anno.AnnValString) annoValue).value;
                    }
                    annoValue = ann.values.get("copyItf");
                    if (annoValue != null && annoValue.type == roj.asm.struct.anno.AnnotationType.BOOLEAN) {
                        if (list == null)
                            list = new ArrayList<>();
                        if (((roj.asm.struct.anno.AnnValInt) annoValue).value == 1) {
                            for (CstClass clz : data.interfaces) {
                                list.add(clz.getValue().getString());
                            }
                        }
                    }
                }
            } else if (ann.rawDesc.equals(IMPL_INTERFACE_TYPE)) {
                if (ann.values != null) {
                    roj.asm.struct.anno.AnnVal annoValue = ann.values.get("value");
                    if (annoValue.type == roj.asm.struct.anno.AnnotationType.ARRAY) {
                        if (list == null)
                            list = new ArrayList<>();
                        for (roj.asm.struct.anno.AnnVal value : ((AnnValArray) annoValue).value) {
                            roj.asm.struct.anno.AnnValClass clazz = (roj.asm.struct.anno.AnnValClass) value;
                            list.add(clazz.value.owner);
                        }
                    }
                }
            }
        }
        return className == null ? null : new Object[]{className, list == null ? Collections.emptyList() : list};
    }

    private static String getAnnotationValue(ConstantPool pool, Attribute attribute, String annoClass) {
        if (attribute == null)
            return null;
        AttrAnnotation annotations = attribute instanceof AttrAnnotation ? (AttrAnnotation) attribute : new AttrAnnotation(ANNO_TYPE, new ByteReader(attribute.getRawData()), pool);

        for (roj.asm.struct.anno.Annotation ann : annotations.annotations) {
            if (ann.rawDesc.equals(annoClass)) {
                if (ann.values != null) {
                    roj.asm.struct.anno.AnnVal annoValue = ann.values.get("value");
                    if (annoValue.type == roj.asm.struct.anno.AnnotationType.STRING) {
                        return ((roj.asm.struct.anno.AnnValString) annoValue).value;
                    }
                }
            }
        }
        return null;
    }

    private static Data getAnnotationData(ConstantPool pool, Attribute attribute, String annoClass) {
        if (attribute == null)
            return null;
        AttrAnnotation annotations = attribute instanceof AttrAnnotation ? (AttrAnnotation) attribute : new AttrAnnotation(false, new ByteReader(attribute.getRawData()), pool);

        Data data = new Data();

        for (roj.asm.struct.anno.Annotation ann : annotations.annotations) {
            if (ann.rawDesc.equals(annoClass)) {
                final Map<String, roj.asm.struct.anno.AnnVal> values = ann.values;
                if (values != null) {
                    roj.asm.struct.anno.AnnVal value = values.get("value");
                    if (value.type == roj.asm.struct.anno.AnnotationType.STRING) {
                        data.value = ((roj.asm.struct.anno.AnnValString) value).value;
                    }
                    value = values.get("injectPos");
                    if (value != null && value.type == roj.asm.struct.anno.AnnotationType.INT) {
                        data.injectPos = ((roj.asm.struct.anno.AnnValInt) value).value;
                    }
                    value = values.get("codeAtPos");
                    if (value != null && value.type == roj.asm.struct.anno.AnnotationType.BYTE) {
                        data.codeAtPos = (byte) ((roj.asm.struct.anno.AnnValInt) value).value;
                    }
                    value = values.get("mustHit");
                    if (value != null && value.type == roj.asm.struct.anno.AnnotationType.BOOLEAN) {
                        data.mustHit = ((roj.asm.struct.anno.AnnValInt) value).value == 1;
                    }
                    value = values.get("ignoreParam");
                    if (value != null && value.type == roj.asm.struct.anno.AnnotationType.BOOLEAN) {
                        data.ignoreParam = ((roj.asm.struct.anno.AnnValInt) value).value == 1;
                    }
                    value = values.get("useSuperInject");
                    if (value != null && value.type == roj.asm.struct.anno.AnnotationType.BOOLEAN) {
                        data.useSuperInject = ((roj.asm.struct.anno.AnnValInt) value).value == 1;
                    }
                    value = values.get("isRemapCopy");
                    if (value != null && value.type == roj.asm.struct.anno.AnnotationType.BOOLEAN) {
                        data.isRemapCopy = ((roj.asm.struct.anno.AnnValInt) value).value == 1;
                    }
                }
                break;
            }
        }
        return data.value == null ? null : data;
    }

    private static boolean hasAnnotation(ConstantPool pool, Attribute attribute, String type) {
        if (attribute == null)
            return false;
        AttrAnnotation annotations = attribute instanceof AttrAnnotation ? (AttrAnnotation) attribute : new AttrAnnotation(false, new ByteReader(attribute.getRawData()), pool);
        for (roj.asm.struct.anno.Annotation ann : annotations.annotations) {
            if (type.equals(ann.rawDesc)) {
                return true;
            }
        }
        return false;
    }

    static class LambdaInfo {
        final AttrBootstrapMethods.BootstrapMethod bootstrapMethod;
        final List<InvokeDynamicInsnNode> nodes = new ArrayList<>();

        LambdaInfo(AttrBootstrapMethods.BootstrapMethod bootstrapMethod) {
            this.bootstrapMethod = bootstrapMethod;
            if (debug)
                logger.info("Lambda call found: " + bootstrapMethod.arguments.get(0));
        }
    }

    public static class NiximData {
        public final Map<String, Method> methodMap;
        public final List<Method> copyMethod;
        public final List<Field> copyField;
        public final Map<String, String> methodRename;
        public final IntMap<LambdaInfo> bsm;
        public final String originalClassName;
        @Nonnull
        public final Map<String, Object> properties;
        public final List<String> appendInterfaces;

        public NiximData(Map<String, Method> niximMethods, List<Method> copyMethod, Map<String, String> methodRename, List<Field> copyField, IntMap<LambdaInfo> lambdaBSM, String originalClassName, Map<String, Object> properties, List<String> appendInterfaces) {
            this.methodMap = niximMethods;
            this.copyMethod = copyMethod;
            this.methodRename = methodRename;
            this.copyField = copyField;

            this.bsm = lambdaBSM;
            this.originalClassName = originalClassName;
            this.properties = properties.isEmpty() ? Collections.emptyMap() : properties;
            this.appendInterfaces = appendInterfaces;

            for (Field field : copyField) {
                for (Attribute attribute : field.attributes) {
                    if (attribute.name.equals(ANNO_TYPE)) {
                        removeNiximAnnotation((AttrAnnotation) attribute);
                    }
                }
            }
            for (Method method : copyMethod) {
                if (method.getInvisibleAnnotations() != null) {
                    removeNiximAnnotation(method.getInvisibleAnnotations());
                }
                if (method.code.attributes.removeByName("LocalVariableTypeTable")) {
                    if (debug)
                        logger.info("Remove [copy] LVTT for " + method.name() + method.rawDesc());
                }
            }
            for (Method method : niximMethods.values()) {
                if (method.getInvisibleAnnotations() != null) {
                    removeNiximAnnotation(method.getInvisibleAnnotations());
                }
                if (method.code.attributes.removeByName("LocalVariableTypeTable")) {
                    if (debug)
                        logger.info("Remove LVTT for " + method.name() + method.rawDesc());
                }
            }
        }

        private static void removeNiximAnnotation(AttrAnnotation attribute) {
            attribute.annotations.removeIf(annotation -> annotation.type.owner.startsWith("roj/asm/nixim"));
        }
    }
}
