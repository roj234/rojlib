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
package ilib.asm.fasterforge.transformers;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.SideOnly;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrAnnotation;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeDynInsnNode;
import roj.asm.util.ConstantPool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SideTransformer implements IClassTransformer {
    private static final String SIDE = FMLLaunchHandler.side().name();

    private static final boolean DEBUG = false;

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null)
            return null;
        ConstantData classNode = Parser.parseConstants(bytes);

        ConstantPool pool = classNode.cp;

        if (remove(pool, classNode.attrByName("RuntimeVisibleAnnotations"), SIDE))
            throw new RuntimeException(String.format("Attempted to load class %s for invalid side %s", classNode.name, SIDE));

        classNode.fields.removeIf(field -> remove(pool, field.attrByName("RuntimeVisibleAnnotations"), SIDE));

        LambdaGatherer lambdaGatherer = new LambdaGatherer(classNode.attrByName("BootstrapMethods"), pool);

        Iterator<MethodSimple> it = classNode.methods.iterator();
        while (it.hasNext()) {
            MethodSimple method = it.next();
            if (remove(pool, method.attrByName("RuntimeVisibleAnnotations"), SIDE)) {
                it.remove();
                lambdaGatherer.accept(method);
                System.out.println("Remove method " + method.name.getString());
            }
        }

        List<AttrBootstrapMethods.BootstrapMethod> dynamicLambdaHandles = lambdaGatherer.getDynamicLambdaHandles();
        for (; !dynamicLambdaHandles.isEmpty(); dynamicLambdaHandles = lambdaGatherer.getDynamicLambdaHandles()) {
            lambdaGatherer = new LambdaGatherer(lambdaGatherer);
            it = classNode.methods.iterator();
            while (it.hasNext()) {
                MethodSimple method = it.next();
                // not public
                if ((method.accesses.flag & 0x1000) == 0)
                    continue;
                for (AttrBootstrapMethods.BootstrapMethod dynamicLambdaHandle : dynamicLambdaHandles) {
                    if (method.name.getString().equals(dynamicLambdaHandle.name) && method.type.getString().equals(dynamicLambdaHandle.rawDesc())) {
                        System.out.println("Remove lmd method " + dynamicLambdaHandle);
                        it.remove();
                        lambdaGatherer.accept(method);
                    }
                }
            }
        }

        return Parser.toByteArray(classNode);
    }

    public static final String SIDEONLY_TYPE = SideOnly.class.getName().replace('.', '/');

    private boolean remove(ConstantPool cst, Attribute anns, String side) {
        if (anns == null)
            return false;
        AttrAnnotation annotations = new AttrAnnotation(true, Parser.reader(anns), cst);
        for (Annotation ann : annotations.annotations) {
            if (ann.clazz.equals(SIDEONLY_TYPE))
                if (ann.values != null) {
                    AnnVal value = ann.values.get("value");
                    if (value != null && value.type() == AnnVal.ENUM) {
                        if (!((AnnValEnum) value).value.equals(side)) {
                            return true;
                        }
                    }
                }
        }
        return false;
    }

    private static class LambdaGatherer {
        private static final AttrBootstrapMethods.BootstrapMethod META_FACTORY = new AttrBootstrapMethods.BootstrapMethod("java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", 6);

        private ConstantPool pool;
        private AttrBootstrapMethods bootstrapMethods;

        LambdaGatherer(LambdaGatherer gatherer) {
            this.bootstrapMethods = gatherer.bootstrapMethods;
            this.pool = gatherer.pool;
        }

        LambdaGatherer(Attribute attribute, ConstantPool pool) {
            if (attribute != null) {
                this.bootstrapMethods = new AttrBootstrapMethods(Parser.reader(attribute), pool);
                this.pool = pool;
            }
        }

        private final List<AttrBootstrapMethods.BootstrapMethod> dynamicLambdaHandles = new ArrayList<>();

        public void accept(MethodSimple method) {
            if (bootstrapMethods == null) return;
            Attribute attribute = method.attrByName("Code");
            AttrCode code = new AttrCode(method, attribute.getRawData(), pool);
            for (InsnNode node : code.instructions) {
                if ((node.code & 0xFF) == 0xba) {
                    visitInvokeDynamicInsn((InvokeDynInsnNode) node);
                    throw new RuntimeException("Fount");
                }
            }
        }

        public void visitInvokeDynamicInsn(InvokeDynInsnNode node) {
            AttrBootstrapMethods.BootstrapMethod bsm = bootstrapMethods.methods.get(node.tableIdx);
            if (META_FACTORY.equals0(bsm)) {
                System.out.println(bsm);
                this.dynamicLambdaHandles.add(bsm);
                throw new RuntimeException("Fount2");
            }
        }

        public List<AttrBootstrapMethods.BootstrapMethod> getDynamicLambdaHandles() {
            return this.dynamicLambdaHandles;
        }
    }
}
