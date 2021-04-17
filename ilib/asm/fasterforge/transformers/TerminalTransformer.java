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
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.FMLSecurityManager;
import roj.asm.Opcodes;
import roj.asm.cst.CstRef;
import roj.asm.type.ParamHelper;
import roj.asm.visitor.*;
import roj.util.ByteList;

public class TerminalTransformer extends CodeVisitor implements IClassTransformer {
    ClassVisitor cv;
    public TerminalTransformer() {
        cv = new ClassVisitor();
        AsIsAttributeVisitor aiav = new AsIsAttributeVisitor(cv);

        cv.attributeVisitor = aiav;
        cv.fieldVisitor = new IVisitor();
        MethodVisitor mv = new MethodVisitor() {
            @Override
            public void visitNode(int acc, String name, String desc, int count) {
                mName = name;
                mDesc = desc;
            }
        };
        mv.attributeVisitor = aiav;
        cv.methodVisitor = mv;
        mv.codeVisitor = this;
        this.attributeVisitor = aiav;
        preVisit(cv);
    }

    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;
        check(clsName = name);
        ByteList out = cv.visit(new ByteList(basicClass));

        return dirty ? out.toByteArray() : basicClass;
    }

    static String clsName, mName, mDesc;

    @Override
    public void invoke(byte code, CstRef method) {
        String owner = method.getClassName();
        if(owner.length() < 16 || owner.length() > 17 || !owner.startsWith("java/lang/")) {
            super.invoke(code, method);
            return;
        }
        String name = method.desc().getName().getString();
        String desc = method.desc().getType().getString();
        switch (owner) {
            case "java/lang/System":
                if (code == Opcodes.INVOKESTATIC && name.equals("exit") && desc.equals("(I)V")) {
                    if (warn) {
                        FMLLog.log.warn("=============================================================");
                        FMLLog.log.warn("MOD HAS DIRECT REFERENCE System.exit() THIS IS NOT ALLOWED REROUTING TO FML!");
                        FMLLog.log.warn("Offender: {}.{}{}", clsName, mName, mDesc);
                        FMLLog.log.warn("Use FMLCommonHandler.exitJava instead");
                        FMLLog.log.warn("=============================================================");
                    }
                    bw.writeByte(code).writeShort(cw.getMethodRefId(callbackOwner, "systemExitCalled", desc));
                    dirty = true;
                    return;
                }
                break;
            case "java/lang/Runtime":
                if (code != Opcodes.INVOKEVIRTUAL && desc.equals("(I)V") && name.length() == 4) {
                    switch (name) {
                        case "exit":
                        case "halt":
                            warn();
                            bw.writeByte(code).writeShort(cw.getMethodRefId(callbackOwner, "runtimeExitCalled", desc));
                            dirty = true;
                            return;
                    }

                }
                break;
        }
        super.invoke(code, method);
    }

    private void warn() {
        if (warn) {
            FMLLog.log.warn("=============================================================");
            FMLLog.log.warn("MOD直接退出JAVA, 这是不允许的!");
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
