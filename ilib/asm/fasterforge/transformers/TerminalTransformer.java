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
import roj.asm.Opcodes;
import roj.asm.cst.Constant;
import roj.asm.cst.CstRef;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.Context;
import roj.asm.util.InsnList;
import roj.asm.visitor.AsIsAttributeVisitor;
import roj.asm.visitor.CodeVisitor;
import roj.util.ByteList;
import roj.util.ByteReader;

import net.minecraftforge.fml.common.FMLLog;

import java.util.List;

public class TerminalTransformer extends CodeVisitor implements ContextClassTransformer {
    public TerminalTransformer() {}

    private TerminalTransformer(int unused) {
        bw = new ByteList();
        br = new ByteReader();

        attributeVisitor = new AsIsAttributeVisitor();
        attributeVisitor.bw = bw;
        attributeVisitor.br = br;
    }

    @Override
    public void transform(String trName, Context ctx) {
        if (!check(clsName = trName)) return;

        ConstantData data = ctx.getData();

        mName = "IMPLIB快速常量检查";
        mDesc = "";

        boolean doVisit = false;
        List<Constant> csts = data.cp.getConstants();
        for (int i = 0; i < csts.size(); i++) {
            Constant c = csts.get(i);
            if (c.type() == Constant.METHOD) {
                CstRef ref = (CstRef) c;
                if (ref.matches("java/lang/System", "exit", "(I)V")) {
                    warn();
                    ref.setClazz(data.cp.getClazz(callbackOwner));
                    ref.desc(data.cp.getDesc("systemExitCalled", "(I)V"));
                } else if (ref.getClassName().equals("java/lang/Runtime")) {
                    String n = ref.desc().getName().getString();
                    if (n.equals("halt") || n.equals("exit")) {
                        doVisit = true;
                    }
                }
            }
        }

        if (doVisit) {
            TerminalTransformer visitor = new TerminalTransformer(1);
            visitor.clsName = clsName;
            visitor.doVisit(data);
        }
    }

    private void doVisit(ConstantData data) {
        attributeVisitor.cw = cw = data.cp;

        List<? extends MethodNode> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodNode method = methods.get(i);
            mName = method.name();
            mDesc = method.rawDesc();

            Attribute attr = (Attribute) method.attributes().getByName("Code");
            if (attr != null) {
                dirty = false;

                if (attr instanceof AttrCode) {
                    traverseAndFilter((AttrCode) attr);
                } else {
                    br.refresh(attr.getRawData());
                    bw.clear();
                    visit(cw);
                    if (dirty) {
                        byte[] slice = new byte[bw.wIndex() - 6];
                        System.arraycopy(bw.list, 6, slice, 0, slice.length);
                        ((AttrUnknown) attr).setRawData(new ByteList(slice));
                    }
                }
            }
        }

        //attributeVisitor.cw = cw = attributeVisitor.cp = cp = null;
    }

    private static boolean traverseAndFilter(AttrCode attr) {
        boolean dirty = false;
        // 有时间了把Interpreter改成二进制模式
        InsnList insn = attr.instructions;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.nodeType() == InsnNode.T_INVOKE) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.owner.equals("java/lang/Runtime")) {
                    if (node1.name.equals("exit") || node1.name.equals("halt")) {
                        dirty = true;
                        node1.owner = callbackOwner;
                        node1.name = node1.name.equals("exit") ? "runtimeExitCalled" : "runtimeHaltCalled";
                        node1.fullDesc("(Ljava/lang/Runtime;I)V");
                    }
                }
            }
        }

        if (dirty) {
           // attr.interpretFlags = AttrCode.COMPUTE_FRAMES;
        }
        return dirty;
    }

    String clsName, mName, mDesc;

    @Override
    public void invoke(byte code, CstRef method) {
        if (code == Opcodes.INVOKEVIRTUAL && "java/lang/Runtime".equals(method.getClassName())) {
            String name = method.desc().getName().getString();
            String desc = method.desc().getType().getString();
            if (desc.equals("(I)V") && name.length() == 4) {
                switch (name) {
                    case "exit":
                    case "halt":
                        warn();
                        bw.put(Opcodes.INVOKESTATIC)
                          .putShort(cw.getMethodRefId(callbackOwner,
                                                      name.equals("exit") ? "runtimeExitCalled" : "runtimeHaltCalled", "(Ljava/lang/Runtime;I)V"));
                        dirty = true;
                        return;
                }
            }
        }
        super.invoke(code, method);
    }

    private void warn() {
        FMLLog.log.warn("=============================================================");
        FMLLog.log.warn("不允许MOD直接退出JAVA!");
        FMLLog.log.warn("来自: {}.{}{}", clsName, mName, mDesc);
        FMLLog.log.warn("请使用 FMLCommonHandler.exitJava();");
        FMLLog.log.warn("=============================================================");
    }

    private boolean dirty;

    private static final String callbackOwner = "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer$ExitVisitor".replace('.', '/');

    public boolean check(String clsName) {
        return (!clsName.equals("net.minecraft.client.Minecraft") && !clsName.equals("net.minecraft.server.dedicated.DedicatedServer") && !clsName
                .equals("net.minecraft.server.dedicated.ServerHangWatchdog") && !clsName.equals("net.minecraft.server.dedicated.ServerHangWatchdog$1") && !clsName
                .equals("net.minecraftforge.fml.common.FMLCommonHandler") && !clsName.startsWith("com.jcraft.jogg.") && !clsName
                .startsWith("scala.sys.") && !clsName.startsWith("net.minecraft.server.gui.MinecraftServerGui") && !clsName
                .startsWith("com.sun.jna."));
    }
}
