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
import roj.asm.cst.CstType;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.ParamHelper;
import roj.asm.util.Context;
import roj.asm.visitor.AsIsAttributeVisitor;
import roj.asm.visitor.CodeVisitor;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.FMLSecurityManager;

import java.util.List;

public class TerminalTransformer extends CodeVisitor implements ContextClassTransformer {
    public TerminalTransformer() {
        bw = new ByteWriter();
        br = new ByteReader();

        attributeVisitor = new AsIsAttributeVisitor();
        attributeVisitor.bw = bw;
        attributeVisitor.br = br;
    }

    @Override
    public void transform(String transformedName, Context context) {
        check(clsName = transformedName);

        ConstantData data = context.getData();

        mName = "<IMPLIB快速常量池检查>";
        mDesc = "<无法获得>";

        boolean doVisit = false;
        List<Constant> csts = data.cp.getConstants();
        for (int i = 0; i < csts.size(); i++) {
            Constant c = csts.get(i);
            if (c.type() == CstType.METHOD) {
                CstRef ref = (CstRef) c;
                if (ref.desc().getType().getString().equals("(I)V")) {
                    if (ref.getClassName().equals("java/lang/System") &&
                            ref.desc().getName().getString().equals("exit")) {
                        warn();
                        ref.setClazz(data.cp.getClazz(callbackOwner));
                    } else if (ref.getClassName().equals("java/lang/Runtime")) {
                        String n = ref.desc().getName().getString();
                        if (n.equals("halt") || n.equals("exit")) {
                            doVisit = true;
                        }
                    }
                }
            }
        }

        if (doVisit) {
            dirty = false;

            attributeVisitor.cw = cw = data.cp;

            List<? extends MethodNode> methods = data.methods;
            for (int i = 0; i < methods.size(); i++) {
                MethodNode method = methods.get(i);
                mName = method.name();
                mDesc = method.rawDesc();

                Attribute attr = (Attribute) method.attributes().getByName("Code");
                if (attr != null) {
                    if (attr instanceof AttrCode) {
                        attr.toByteArray(cw, bw);
                    } else {
                        br.refresh(attr.getRawData());
                        bw.list.clear();
                        visit(cw);
                        if (dirty) {
                            attr.getRawData().setValue(bw.toByteArray());
                        }
                    }
                }
            }

            attributeVisitor.cw = cw = attributeVisitor.cp = cp = null;
        }
    }

    static String clsName, mName, mDesc;

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
                        bw.writeByte(Opcodes.INVOKESTATIC)
                          .writeShort(cw.getMethodRefId(callbackOwner,
                                                        name.equals("exit") ? "runtimeExitCalled" : "runtimeHaltCalled", "(I)V"));
                        dirty = true;
                        return;
                }
            }
        }
        super.invoke(code, method);
    }

    private void warn() {
        if (warn) {
            FMLLog.log.warn("=============================================================");
            FMLLog.log.warn("不允许MOD直接退出JAVA!");
            FMLLog.log.warn("来自: {}.{}{}", clsName, mName, mDesc);
            FMLLog.log.warn("请使用 FMLCommonHandler.exitJava();");
            FMLLog.log.warn("=============================================================");
        }
    }

    private boolean dirty, warn;

    private static final String callbackOwner = ParamHelper.classDescriptor(TerminalTransformer.class);

    public void check(String clsName) {
        warn = (!clsName.equals("net/minecraft/client/Minecraft") && !clsName.equals("net/minecraft/server/dedicated/DedicatedServer") && !clsName
                .equals("net/minecraft/server/dedicated/ServerHangWatchdog") && !clsName.equals("net/minecraft/server/dedicated/ServerHangWatchdog$1") && !clsName
                .equals("net/minecraftforge/fml/common/FMLCommonHandler") && !clsName.startsWith("com/jcraft/jogg/") && !clsName
                .startsWith("scala/sys/") && !clsName.startsWith("net/minecraft/server/gui/MinecraftServerGui") && !clsName
                .startsWith("com/sun/jna/"));
    }

    public static void systemExitCalled(int status) {
        checkAccess();
        System.exit(status);
    }

    public static void runtimeExitCalled(Runtime runtime, int status) {
        checkAccess();
        runtime.exit(status);
    }

    public static void runtimeHaltCalled(Runtime runtime, int status) {
        checkAccess();
        runtime.halt(status);
    }

    private static void checkAccess() {
        StackTraceElement[] cause = Thread.currentThread().getStackTrace();
        String callingClass = (cause.length > 2) ? cause[3].getClassName() : "none";
        String callingParent = (cause.length > 3) ? cause[4].getClassName() : "none";
        if (!(callingClass.startsWith("net.minecraftforge.fml.") || (callingClass.equals("net.minecraft.client.Minecraft") && callingParent.equals("net.minecraft.client.Minecraft")) || (callingClass.equals("net.minecraft.server.gui.MinecraftServerGui$1") && callingParent.equals("java.awt.AWTEventMulticaster")) || (callingClass.equals("net.minecraft.server.dedicated.DedicatedServer") && callingParent.equals("net.minecraft.server.MinecraftServer")) || callingClass.equals("net.minecraft.server.dedicated.ServerHangWatchdog") || callingClass.equals("net.minecraft.server.dedicated.ServerHangWatchdog$1")))
            throw new FMLSecurityManager.ExitTrappedException();
    }
}
