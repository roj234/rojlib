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

import ilib.api.ContextClassTransformer;
import ilib.asm.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.SideOnly;
import roj.asm.Parser;
import roj.asm.cst.CstDynamic;
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstRef;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.AttrBootstrapMethods.BootstrapMethod;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.asm.util.Context;
import roj.asm.visitor.CodeVisitor;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.util.List;

public final class SideTransformer implements ContextClassTransformer {
    private static final String SIDE = FMLLaunchHandler.side().name();
    private static MyHashSet<String> has;

    public static void lockdown() {
        has = new MyHashSet<>();
        for (ASMDataTable.ASMData data : Loader.ASMTable.getAll(SideOnly.class.getName())) {
            has.add(data.getClassName());
        }
    }

    @Override
    public void transform(String trName, Context ctx) {
        if (has != null && !has.contains(trName)) return;
        ConstantData data = ctx.getData();
        ConstantPool pool = data.cp;

        if (remove(pool, data.attrByName("RuntimeVisibleAnnotations"), SIDE))
            throw new RuntimeException(data.name + " 不应在 " + SIDE + " 加载");

        List<? extends FieldNode> fields = data.fields;
        for (int i = fields.size() - 1; i >= 0; i--) {
            FieldNode sp = fields.get(i);
            if (remove(pool, sp.attrByName("RuntimeVisibleAnnotations"), SIDE)) {
                fields.remove(i);
            }
        }

        LambdaGatherer gatherer = new LambdaGatherer(data.attrByName("BootstrapMethods"), pool);
        gatherer.owner = data.name;

        List<? extends MethodNode> methods = data.methods;
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodNode method = methods.get(i);
            if (remove(pool, method.attrByName("RuntimeVisibleAnnotations"), SIDE)) {
                methods.remove(i);
                gatherer.accept(method);
            }
        }

        List<AttrBootstrapMethods.BootstrapMethod> handles;
        do {
            handles = new SimpleList<>(gatherer.handles);
            gatherer.handles.clear();

            for (int i = methods.size() - 1; i >= 0; i--) {
                MethodNode m = methods.get(i);
                if ((m.accessFlag() & AccessFlag.SYNTHETIC) == 0) continue;
                for (int j = 0; j < handles.size(); j++) {
                    CstNameAndType method = handles.get(j).implementor().desc();
                    if (m.name().equals(method.getName().getString()) &&
                        m.rawDesc().equals(method.getType().getString())) {
                        methods.remove(i);
                        gatherer.accept(m);
                        break;
                    }
                }
            }
        } while (!handles.isEmpty());
    }

    public static final String SIDEONLY_TYPE = SideOnly.class.getName().replace('.', '/');

    private boolean remove(ConstantPool cst, Attribute anns, String side) {
        if (anns == null) return false;

        ByteReader r = Parser.reader(anns);
        for (int i = r.readUnsignedShort(); i > 0; i--) {
            Annotation ann = Annotation.deserialize(cst, r);
            if (ann.clazz.equals(SIDEONLY_TYPE)) {
                if (!ann.getEnum("value").value.equals(side)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class LambdaGatherer extends CodeVisitor {
        private static final AttrBootstrapMethods.BootstrapMethod META_FACTORY = new AttrBootstrapMethods.BootstrapMethod("java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", 6);

        String owner;
        private List<BootstrapMethod> bootstrapMethods;

        LambdaGatherer(Attribute attribute, ConstantPool pool) {
            cw = pool;
            bw = new ByteList.Streamed();
            br = new ByteReader();
            if (attribute != null) {
                bootstrapMethods = AttrBootstrapMethods.parse(Parser.reader(attribute), pool);
            }
        }

        final List<AttrBootstrapMethods.BootstrapMethod> handles = new SimpleList<>();

        public void accept(MethodNode method) {
            if (bootstrapMethods == null) return;

            bw.clear();
            br.refresh(method.attrByName("Code").getRawData());
            visit(cw);
        }

        @Override
        public void invoke_dynamic(CstDynamic dyn, int type) {
            AttrBootstrapMethods.BootstrapMethod handle = bootstrapMethods.get(dyn.tableIdx);
            if (META_FACTORY.equals0(handle)) {
                CstRef method = handle.implementor();
                if (method.getClassName().equals(owner)) {
                    handles.add(handle);
                }
            }
        }
    }
}
