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

import ilib.asm.Loader;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.tree.attr.AttrAnnotation;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrLocalVars;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.insn.LoadConstInsnNode;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.tree.simple.MoFNode;
import roj.asm.type.LocalVariable;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.MyHashSet;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static roj.asm.tree.anno.AnnotationType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class AutoRegisterTransformer implements IClassTransformer {
    /**
     * TileEntity: <clinit>
     * ldc self
     * ldc value
     * invokestatic AutoRegisterTransformer.register($0, value)
     * <p>
     * Block:
     * new $0
     * invspec $0
     * ldc
     * ldc
     * ldc
     * invokestatic AutoRegisterTransformer
     */
    static final String ANNOTATION_TYPE = "RuntimeInvisibleAnnotations";
    static Set<String> classes;

    public static void initStore() {
        if(classes == null) {
            classes = new MyHashSet<>();
        }
        classes.clear();

        Consumer<ASMDataTable.ASMData> adder = (c) -> classes.add(c.getClassName());

        Set<ASMDataTable.ASMData> data1 = Loader.asmInfo.getAll("ilib.autoreg.AutoRegItem");
        data1.forEach(adder);
        Set<ASMDataTable.ASMData> data2 = Loader.asmInfo.getAll("ilib.autoreg.AutoRegBlock");
        data2.forEach(adder);
        Set<ASMDataTable.ASMData> data3 = Loader.asmInfo.getAll("ilib.autoreg.AutoRegTile");
        data3.forEach(adder);
        Set<ASMDataTable.ASMData> data4 = Loader.asmInfo.getAll("ilib.autoreg.RegHolder");
        data4.forEach(adder);
    }

    public static void handlePreInit() {
        for (String s : classes) {
            try {
                Class.forName(s, true, Launch.classLoader);
            } catch (ExceptionInInitializerError | ClassNotFoundException e) {
                throw new RuntimeException("Class " + s + " is throwing error during it's class initialization progress! This is NOT an ImpLib bug! This is caused by this class's writer!", e);
            } catch (ClassFormatError | VerifyError e) {
                throw new RuntimeException("ART Internal error! Report this to author!!!!", e);
            }
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;
        if (classes != null) {
            if (!classes.remove(transformedName)) {
                if (classes.isEmpty())
                    classes = null;
                return basicClass;
            }
        } else {
            return basicClass;
        }

        ConstantData data = Parser.parseConstants(basicClass);
        Attribute attribute = data.attrByName(ANNOTATION_TYPE);
        if (attribute != null) {
            AttrAnnotation annotations = new AttrAnnotation(true, new ByteReader(attribute.getRawData()), data.cp);
            for (roj.asm.tree.anno.Annotation annotation : annotations.annotations) {
                switch (annotation.type.owner) {
                    case "ilib/autoreg/AutoRegItem":
                        return transformItem(data, annotation.values);
                    case "ilib/autoreg/AutoRegBlock":
                        return transformBlock(data, annotation.values);
                    case "ilib/autoreg/AutoRegTile":
                        return transformTileEntity(data, (roj.asm.tree.anno.AnnValString) annotation.values.get("value"));
                }
            }
        }

        boolean modified = false;

        outer:
        for (FieldSimple fs : data.fields) {
            attribute = data.attrByName(ANNOTATION_TYPE);
            if (attribute == null)
                continue;
            AttrAnnotation annotations = new AttrAnnotation(true, new ByteReader(attribute.getRawData()), data.cp);
            for (roj.asm.tree.anno.Annotation annotation : annotations.annotations) {
                switch (annotation.type.owner) {
                    case "ilib/autoreg/RegHolder":
                        transformRegHolder(data, (roj.asm.tree.anno.AnnValString) annotation.values.get("value"), fs);
                        modified = true;
                        continue outer;
                }
            }
        }

        return modified ? Parser.toByteArray(data) : basicClass;
    }

    private static void transformRegHolder(ConstantData data, roj.asm.tree.anno.AnnValString value, FieldSimple field) {
        AttrCode code = getOrCreateClInit(data);

        final InsnList insn = code.instructions;

        roj.asm.tree.insn.InsnNode returnNode = insn.remove(insn.size() - 1);

        if (code.stackSize < 3)
            code.stackSize = 3;

        // value = null : error

        insn.add(new roj.asm.tree.insn.LoadConstInsnNode(Opcodes.LDC, new CstString(':' + value.value)));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/autoreg/AutoRegHandler.get:(Ljava/lang/String;)Ljava/lang/Object;"));

        Type desc = roj.asm.type.ParamHelper.parseField(field.type.getString());

        // desc.className = null : not reference type

        insn.add(new roj.asm.tree.insn.ClassInsnNode(Opcodes.CHECKCAST, desc.owner));
        insn.add(new roj.asm.tree.insn.FieldInsnNode(Opcodes.PUTSTATIC, data.name, field.name.getString(), desc));

        insn.add(returnNode);

        insn.add(AttrCode.METHOD_END_MARK);
    }

    private static byte[] transformTileEntity(ConstantData data, roj.asm.tree.anno.AnnValString value) {
        AttrCode code = getOrCreateClInit(data);

        final InsnList insn = code.instructions;

        roj.asm.tree.insn.InsnNode returnNode = insn.remove(insn.size() - 1);

        if (code.stackSize < 3)
            code.stackSize = 3;

        insn.add(new roj.asm.tree.insn.LoadConstInsnNode(Opcodes.LDC, data.nameCst));
        insn.add(loadParam(value, null));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/autoreg/AutoRegHandler.registerTileEntity:(Ljava/lang/Class;Ljava/lang/String;)V"));

        insn.add(returnNode);

        insn.add(AttrCode.METHOD_END_MARK);

        return Parser.toByteArray(data);
    }

    private static byte[] transformBlock(ConstantData data, Map<String, roj.asm.tree.anno.AnnVal> mapping) {
        AttrCode code = getOrCreateClInit(data);

        final InsnList insn = code.instructions;

        roj.asm.tree.insn.InsnNode returnNode = insn.remove(insn.size() - 1);

        if (code.stackSize < 6)
            code.stackSize = 6;

        AttrLocalVars localVars;
        if ((localVars = (AttrLocalVars) code.attributes.getByName("LocalVariableTable")) == null) {
            code.attributes.add(localVars = new AttrLocalVars("LocalVariableTable"));
        }

        int nextVariableId = code.localSize++;

        roj.asm.tree.insn.InsnNode startNode, endNode;

        insn.add(new roj.asm.tree.insn.ClassInsnNode(Opcodes.NEW, data.nameCst));
        insn.add(new roj.asm.tree.insn.NPInsnNode(Opcodes.DUP));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, data.name, "<init>", "()V"));
        insn.add(startNode = new roj.asm.tree.insn.NPInsnNode(Opcodes.DUP));

        NodeHelper.compress(insn, Opcodes.ASTORE, nextVariableId);

        insn.add(loadParam(mapping.get("value"), null));

        // block
        // string

        String string = mapping.containsKey("item") ? asString(mapping.get("item")) : "";
        String className;
        switch (string) {
            case "":
                className = "net/minecraft/item/ItemBlock";
                break;
            case "<null>":
                className = null;
                break;
            default:
                className = string;
        }

        if (className == null) {
            insn.add(new roj.asm.tree.insn.NPInsnNode(Opcodes.ACONST_NULL));
        } else {
            CstClass clz = data.writer.getClazz(className);
            insn.add(new roj.asm.tree.insn.ClassInsnNode(Opcodes.NEW, clz));
            insn.add(new roj.asm.tree.insn.NPInsnNode(Opcodes.DUP));

            NodeHelper.compress(insn, Opcodes.ALOAD, nextVariableId);

            insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, className, "<init>", "(Lnet/minecraft/block/Block;)V"));
        }

        insn.add(loadParam(mapping.get("model"), "DEFAULT"));
        insn.add(loadParam(mapping.get("modelPath"), ""));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/autoreg/AutoRegHandler.registerBlock:(Lnet/minecraft/block/Block;Ljava/lang/String;Lnet/minecraft/item/Item;Ljava/lang/String;Ljava/lang/String;)V"));

        insn.add(endNode = returnNode);

        localVars.list.add(new LocalVariable(nextVariableId, "null", new Type("net/minecraft/block/Block", 0), startNode, endNode));

        insn.add(AttrCode.METHOD_END_MARK);

        byte[] bytes = Parser.toByteArray(data);
        try (FileOutputStream fos = new FileOutputStream(new File(data.name.replace('/', '.') + ".class"))) {
            fos.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }

    private static byte[] transformItem(ConstantData data, Map<String, roj.asm.tree.anno.AnnVal> mapping) {
        AttrCode code = getOrCreateClInit(data);

        final InsnList insn = code.instructions;

        roj.asm.tree.insn.InsnNode returnNode = insn.remove(insn.size() - 1);

        if (code.stackSize < 5)
            code.stackSize = 5;

        insn.add(new roj.asm.tree.insn.ClassInsnNode(Opcodes.NEW, data.nameCst));
        insn.add(new roj.asm.tree.insn.NPInsnNode(Opcodes.DUP));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, data.name, "<init>", "()V"));
        insn.add(loadParam(mapping.get("value"), null));

        insn.add(loadParam(mapping.get("model"), "DEFAULT"));

        if (mapping.get("modelPath") != null) {
            insn.add(loadParam(mapping.get("modelPath"), ""));
        } else {
            insn.add(new roj.asm.tree.insn.NPInsnNode(Opcodes.ACONST_NULL));
        }
        insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/autoreg/AutoRegHandler.registerItem:(Lnet/minecraft/item/Item;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"));

        insn.add(returnNode);

        insn.add(AttrCode.METHOD_END_MARK);

        return Parser.toByteArray(data);
    }

    private static roj.asm.tree.insn.LoadConstInsnNode loadParam(roj.asm.tree.anno.AnnVal value, String defaultValue) {
        return new LoadConstInsnNode(Opcodes.LDC, new CstString(value == null ? defaultValue : asString(value)));
    }

    private static String asString(roj.asm.tree.anno.AnnVal value) {
        switch (value.type) {
            case ENUM:
                return ((AnnValEnum) value).value;
            case CLASS:
                return ParamHelper.getField(((roj.asm.tree.anno.AnnValClass) value).value);
            case STRING:
                return ((roj.asm.tree.anno.AnnValString) value).value;
        }
        throw new RuntimeException();
    }

    @SuppressWarnings("unchecked")
    private static AttrCode getOrCreateClInit(ConstantData data) {
        Method clInit = null;

        ListIterator<MoFNode> itr = Helpers.cast(data.methods.listIterator());

        while (itr.hasNext()) {
            MethodSimple ms = (MethodSimple) itr.next();
            if (ms.name().equals("<clinit>")) {
                clInit = new Method(data, ms);
                itr.set(clInit);
                break;
            }
        }

        AttrCode code;

        if (clInit == null) {
            clInit = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, data, "<clinit>", "()V");
            ((List<MoFNode>) Helpers.cast(data.methods)).add(clInit);
            code = clInit.code = new AttrCode(clInit);
            code.instructions.add(new roj.asm.tree.insn.NPInsnNode(Opcodes.RETURN));
            code.instructions.add(AttrCode.METHOD_END_MARK);
        } else {
            code = clInit.code;
        }

        if (!code.instructions.isEmpty()) {
            code.instructions.remove(code.instructions.size() - 1); // END
            //code.code.instructionList.remove(code.instructionList.size() - 1); // RETURN
        }

        return code;
    }
}
