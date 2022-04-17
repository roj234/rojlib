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

package ilib.command.parser;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import roj.asm.TransformException;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.asm.util.InsnList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.INVOKESPECIAL;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CommandNeXt extends CommandBase {
    public String name;
    public int permission;

    private List<String> aliases;

    static List<String> empty_list = Collections.emptyList();

    static Map<Class<?>, ArgumentHandler<?>> decoders;

    private interface ASMInvoker {
        void invoke(Object instance, Object[] data);
    }

    public static void initStore() {
        // todo
    }

    public static void from(Context ctx) throws TransformException {
        ConstantData data = ctx.getData();
        List<Annotation> annotations = AttrHelper.getAnnotations(data.cp, data, false);
        if (annotations == null) throw new TransformException("没有找到EVC注解");
        block:{
            for (int i = 0; i < annotations.size(); i++) {
                Annotation a = annotations.get(i);
                if (a.clazz.endsWith("EnumViaConfig")) {
                    break block;
                }
            }
            throw new TransformException("没有找到EVC注解");
        }

        Method clInit = data.getUpgradedMethod("<clinit>", "()V");
        if (clInit == null) {
            clInit = new Method(AccessFlag.STATIC, data, "<clinit>", "()V");
            clInit.code = new AttrCode(clInit);
            data.methods.add(Helpers.cast(clInit));
        } else {
            // return
            InsnList insn = clInit.code.instructions;
            insn.remove(insn.size() - 1);
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                if (node.getOpcode() == INVOKESPECIAL) {
                    InvokeInsnNode iin = (InvokeInsnNode) node;
                    if (iin.owner.equals(data.name) && iin.name.equals("<init>")) {
                        throw new TransformException("不应手动创建任何" + data.name + "对象");
                    }
                }
            }
        }
        clInit.code.interpretFlags = AttrCode.COMPUTE_SIZES | AttrCode.COMPUTE_FRAMES;

        List<? extends MethodNode> ms = data.methods;
        for (int i = 0; i < ms.size(); i++) {
            MethodNode m = ms.get(i);
            List<Annotation> anns = AttrHelper.getAnnotations(data.cp, m, false);
            if (anns == null) continue;
            for (int j = 0; j < anns.size(); j++) {
                Annotation ann = anns.get(j);
                if (ann.clazz.endsWith("EnumViaConfig$Constructor")) {
                    if (!m.name().equals("<init>")) throw new TransformException("构造器注解必须应用于构造器");

                }
            }
        }
    }

    public CommandNeXt(String name, int level, Class<?> target) {
        this.name = name;
        this.permission = level;
    }

   
    public String getName() {
        return name;
    }
   
    public String getUsage(@Nonnull ICommandSender sender) {
        return "command.mi." + name + ".usage";
    }

   
    public List<String> getAliases() {
        return this.aliases == null ? empty_list : this.aliases;
    }

    public void execute(@Nonnull MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
    }

    @Override
    public int getRequiredPermissionLevel() {
        return this.permission;
    }
   
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        return empty_list;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    public CommandNeXt addAliases(String alias) {
        if (this.aliases == null) {
            this.aliases = new ArrayList<>();
            this.aliases.add(getName());
        }
        this.aliases.add(alias);
        return this;
    }
}
