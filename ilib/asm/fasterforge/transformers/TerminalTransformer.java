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
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.type.ParamHelper;

public class TerminalTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;
        ConstantData data = Parser.parseConstants(basicClass, true);
        ExitVisitor visitor = new ExitVisitor();
        visitor.visit(data);
        return visitor.dirty ? Parser.toByteArray(data, true) : basicClass;
    }

    public static class ExitVisitor {
        private String clsName = null;

        private boolean dirty;

        private static final String callbackOwner = ParamHelper.classDescriptor(ExitVisitor.class);
        private String mName;
        private String mDesc;
        private boolean warn;

        private ExitVisitor() {
        }

        public void visit(ConstantData data) {
            this.clsName = data.name;
            warn = (!this.clsName.equals("net/minecraft/client/Minecraft") && !this.clsName.equals("net/minecraft/server/dedicated/DedicatedServer") && !this.clsName.equals("net/minecraft/server/dedicated/ServerHangWatchdog") && !this.clsName.equals("net/minecraft/server/dedicated/ServerHangWatchdog$1") && !this.clsName.equals("net/minecraftforge/fml/common/FMLCommonHandler") && !this.clsName.startsWith("com/jcraft/jogg/") && !this.clsName.startsWith("scala/sys/") && !this.clsName.startsWith("net/minecraft/server/gui/MinecraftServerGui") && !this.clsName.startsWith("com/sun/jna/"));
            for (MethodSimple method : data.methods) {
                this.mName = method.name.getString();
                this.mDesc = method.type.getString();

                AttrCode code = Parser.getOrCreateCode(data, method);

                if (code == null) {
                    continue;
                }
                for (InsnNode node : code.instructions) {
                    if (node instanceof InvokeInsnNode)
                        visitMethodInsn((InvokeInsnNode) node);
                }
            }
        }

        public void visitMethodInsn(InvokeInsnNode node) {
            int opcode = node.code & 0xff;
            String owner = node.owner();
            String name = node.name();
            String desc = node.rawTypes();
            if (opcode == 184 && owner.equals("java/lang/System") && name.equals("exit") && desc.equals("(I)V")) {
                if (warn) {
                    FMLLog.log.warn("=============================================================");
                    FMLLog.log.warn("MOD HAS DIRECT REFERENCE System.exit() THIS IS NOT ALLOWED REROUTING TO FML!");
                    FMLLog.log.warn("Offender: {}.{}{}", TerminalTransformer.ExitVisitor.this.clsName, mName, mDesc);
                    FMLLog.log.warn("Use FMLCommonHandler.exitJava instead");
                    FMLLog.log.warn("=============================================================");
                }
                node.owner(callbackOwner);
                node.name("systemExitCalled");
                TerminalTransformer.ExitVisitor.this.dirty = true;
            } else if (opcode == 182 && owner.equals("java/lang/Runtime") && name.equals("exit") && desc.equals("(I)V")) {
                if (warn) {
                    FMLLog.log.warn("=============================================================");
                    FMLLog.log.warn("MOD HAS DIRECT REFERENCE Runtime.exit() THIS IS NOT ALLOWED REROUTING TO FML!");
                    FMLLog.log.warn("Offender: {}.{}{}", TerminalTransformer.ExitVisitor.this.clsName, mName, mDesc);
                    FMLLog.log.warn("Use FMLCommonHandler.exitJava instead");
                    FMLLog.log.warn("=============================================================");
                }
                node.setOpcode(Opcodes.INVOKESTATIC);
                node.owner(callbackOwner);
                node.name("runtimeExitCalled");
                node.rawTypes("(Ljava/lang/Runtime;I)V");
                TerminalTransformer.ExitVisitor.this.dirty = true;
            } else if (opcode == 182 && owner.equals("java/lang/Runtime") && name.equals("halt") && desc.equals("(I)V")) {
                if (warn) {
                    FMLLog.log.warn("=============================================================");
                    FMLLog.log.warn("MOD HAS DIRECT REFERENCE Runtime.halt() THIS IS NOT ALLOWED REROUTING TO FML!");
                    FMLLog.log.warn("Offendor: {}.{}{}", TerminalTransformer.ExitVisitor.this.clsName, mName, mDesc);
                    FMLLog.log.warn("Use FMLCommonHandler.exitJava instead");
                    FMLLog.log.warn("=============================================================");
                }
                node.setOpcode(Opcodes.INVOKESTATIC);
                node.owner(callbackOwner);
                node.name("runtimeExitCalled");
                node.rawTypes("(Ljava/lang/Runtime;I)V");
                TerminalTransformer.ExitVisitor.this.dirty = true;
            }
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
}
