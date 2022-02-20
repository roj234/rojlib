/*
 * This file is a part of MoreItems
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
package lac.injector.mapper;

import roj.asm.Parser;
import roj.asm.cst.CstRef;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.AttrBootstrapMethods.BootstrapMethod;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeDynInsnNode;
import roj.asm.util.Context;
import roj.asm.util.InsnList;
import roj.collect.HashBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.mapper.util.Desc;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2021/10/17 10:30
 */
public class Mover {
    private final HashBiMap<Desc, Desc> methodMove = new HashBiMap<>();
    private final HashBiMap<String, String> classMove = new HashBiMap<>();

    private Function<Desc, Desc> fallback;

    public void setFallback(Function<Desc, Desc> fallback) {
        this.fallback = fallback;
    }

    public HashBiMap<Desc, Desc> getMethodMove() {
        return methodMove;
    }

    public HashBiMap<String, String> getClassMove() {
        return classMove;
    }

    public void map(List<Context> ctxs) {
        Map<String, List<Movable>> moved = new MyHashMap<>();
        Desc checker = new Desc();
        for (int i = 0; i < ctxs.size(); i++) {
            ConstantData data = ctxs.get(i).getData();
            List<CstRef> refs = ctxs.get(i).getMethodConstants();
            for (int j = 0; j < refs.size(); j++) {
                CstRef ref = refs.get(j);
                Desc desc = methodMove.get(checker.read(ref));
                if (desc == null)
                    desc = methodMove.getByValue(checker);
                if (desc != null) {
                    ref.setClazz(data.cp.getClazz(desc.owner));
                    ref.desc(data.cp.getDesc(desc.name, ref.desc().getType().getString()));
                }
            }
            String name = classMove.get(data.name);
            if (name != null)
                data.cp.setUTFValue(data.nameCst.getValue(), name);
            checker.owner = data.name;
            List<? extends MethodNode> ms = data.methods;
            for (int j = ms.size() - 1; j >= 0; j--) {
                MethodNode m = ms.get(j);
                checker.name = m.name();
                checker.param = m.rawDesc();
                Desc desc = methodMove.get(checker);
                if (desc == null)
                    desc = methodMove.getByValue(checker);
                if (desc != null) {
                    if (!desc.owner.equals(data.name)) {
                        Movable mv = new Movable();
                        Method e = mv.node = m instanceof Method ? (Method) m : new Method(data, (MethodSimple) m);
                        e.name = desc.name;
                        if (!ctxs.get(i).getInvokeDynamic().isEmpty()) {
                            checkLambdaRef(data, mv);
                        }
                        moved.computeIfAbsent(desc.owner, Helpers.fnArrayList()).add(mv);
                        ms.remove(j);
                    } else {
                        if (m instanceof Method) {
                            ((Method) m).name = desc.name;
                        } else {
                            ((SimpleComponent) m).name = data.cp.getUtf(desc.name);
                        }
                    }
                }
            }
        }
        if (moved.isEmpty()) return;
        Map<Desc, Desc> duplicateFallback = new MyHashMap<>();
        MyHashSet<Desc> tmp = new MyHashSet<>();
        for (int i = 0; i < ctxs.size(); i++) {
            ConstantData data = ctxs.get(i).getData();
            List<Movable> append = moved.remove(data.name);
            if (append != null) {
                List<? extends MethodNode> methods = data.methods;
                for (int j = 0; j < append.size(); j++) {
                    methods.add(Helpers.cast(append.get(j).node));
                }
                for (int j = methods.size() - 1; j >= 0; j--) {
                    MethodNode m = methods.get(j);
                    Desc desc = new Desc(data.name, m.name(), m.rawDesc());
                    if (!tmp.add(desc)) {
                        append.remove(m);
                        methods.remove(j);

                        Desc put = duplicateFallback.put(desc, desc = fallback.apply(desc));
                        if (null != put)
                            throw new IllegalArgumentException("Method occur twice: " + m.ownerClass() + '.' + m.name() + ' ' + m.rawDesc());
                        Movable mv = new Movable();
                        Method e = mv.node = m instanceof Method ? (Method) m : new Method(data, (MethodSimple) m);
                        e.name = desc.name;
                        if (!ctxs.get(i).getInvokeDynamic().isEmpty()) {
                            checkLambdaRef(data, mv);
                        }

                        moved.computeIfAbsent(desc.owner, Helpers.fnArrayList()).add(mv);
                    }
                }
                satisfyBSM(ctxs.get(i), append);
                tmp.clear();
            }
        }
        if (duplicateFallback.isEmpty()) return;
        for (int i = 0; i < ctxs.size(); i++) {
            ConstantData data = ctxs.get(i).getData();
            List<CstRef> refs = ctxs.get(i).getMethodConstants();
            for (int j = 0; j < refs.size(); j++) {
                CstRef ref = refs.get(j);
                Desc desc = duplicateFallback.get(checker.read(ref));
                if (desc != null) {
                    ref.setClazz(data.cp.getClazz(desc.owner));
                    ref.desc(data.cp.getDesc(desc.name, ref.desc().getType().getString()));
                }
            }
        }
        if (moved.isEmpty()) return;
        for (int i = 0; i < ctxs.size(); i++) {
            ConstantData data = ctxs.get(i).getData();
            List<Movable> append = moved.remove(data.name);
            if (append != null) {
                List<? extends MethodNode> methods = data.methods;
                for (int j = 0; j < append.size(); j++) {
                    methods.add(Helpers.cast(append.get(j).node));
                }
                satisfyBSM(ctxs.get(i), append);
            }
        }
    }

    private static void satisfyBSM(Context ctx, List<Movable> append) {
        ConstantData data = ctx.getData();
        for (int i = 0; i < append.size(); i++) {
            Movable mv = append.get(i);
            if (!mv.idn.isEmpty()) {
                AttrBootstrapMethods bm = getBSM(data);
                if (bm == null) {
                    data.attributes.putByName(bm = new AttrBootstrapMethods());
                }
                for (Map.Entry<BootstrapMethod, List<InvokeDynInsnNode>> entry : mv.idn.entrySet()) {
                    char id = (char) bm.methods.size();
                    BootstrapMethod clone = entry.getKey().clone();
                    bm.methods.add(clone);
                    for (InvokeDynInsnNode node : entry.getValue()) {
                        node.tableIdx = id;
                    }
                }
            }
        }
    }

    private static void checkLambdaRef(ConstantData data, Movable e) {
        InsnList insn = e.node.code.instructions;
        for (int k = 0; k < insn.size(); k++) {
            InsnNode node = insn.get(k);
            if (node.nodeType() == InsnNode.T_INVOKE_DYNAMIC) {
                AttrBootstrapMethods bm = getBSM(data);
                if (bm == null)
                    throw new IllegalStateException("有InvokeDyn却没有BSM属性 " + data.name);
                if (e.idn.isEmpty())
                    e.idn = new MyHashMap<>();
                InvokeDynInsnNode node1 = (InvokeDynInsnNode) node;
                e.idn.computeIfAbsent(bm.methods.get(node1.tableIdx), Helpers.fnLinkedList()).add(node1);
            }
        }
    }

    private static AttrBootstrapMethods getBSM(ConstantData data) {
        Attribute attr = (Attribute) data.attributes.getByName("BootstrapMethods");
        AttrBootstrapMethods bm;
        if (attr instanceof AttrBootstrapMethods) {
            bm = (AttrBootstrapMethods) attr;
        } else {
            if (attr == null) return null;
            bm = new AttrBootstrapMethods(Parser.reader(attr), data.cp);
            data.attributes.putByName(bm);
        }
        return bm;
    }

    public void put(Desc from, Desc to) {
        from = from.copy();
        from.param = "";
        if (methodMove.containsKey(to) || methodMove.containsValue(from))
            throw new IllegalStateException("Duplicate mapping! " + from + " => " + to + "\n existA: " + methodMove.get(from) + " => " + from + "\n existB: " + to + " => " + methodMove.get(to));
        methodMove.forcePut(from, to);
    }

    public void putClass(String from, String to) {
        classMove.put(from, to);
    }

    public void clear() {
        methodMove.clear();
        classMove.clear();
    }

    public boolean putIfAbsent(Desc from, Desc to) {
        from = from.copy();
        from.param = "";
        if (methodMove.containsKey(to) || methodMove.containsValue(from))
            return false;
        methodMove.forcePut(from, to);
        return true;
    }

    private static final class Movable {
        Method node;
        Map<AttrBootstrapMethods.BootstrapMethod, List<InvokeDynInsnNode>> idn = Collections.emptyMap();
    }
}
