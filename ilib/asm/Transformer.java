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

package ilib.asm;

import ilib.Config;
import ilib.api.ContextClassTransformer;
import ilib.client.api.ClientChangeWorldEvent;
import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.insn.*;
import roj.asm.util.Context;
import roj.asm.util.InsnList;
import roj.collect.IntBiMap;
import roj.collect.WeakHashSet;
import roj.util.Helpers;

import net.minecraft.entity.Entity;

import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Set;

import static ilib.asm.Loader.logger;

/**
 * 标准(ASM)class转换器
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class Transformer implements ContextClassTransformer {
    public static long MSpT = 50L;

    @Override
    public void transform(String trName, Context ctx) {
        /*if(trName.contains(".Entity")) {
            return transformEntityDataManager(data);
        }*/
        switch (trName) {
            case "net.minecraft.util.math.MathHelper":
                MathOptimizer.optimizeMathHelper(ctx);
                break;
            case "net.minecraft.client.multiplayer.WorldClient":
                if (Config.replaceEntityList) transformWorldClient(ctx);
                break;
            case "net.minecraft.entity.item.EntityMinecart":
                if (Config.fixMinecart) transformMinecart(ctx);
                break;
            case "net.minecraft.client.Minecraft":
                transformMinecraft(ctx);
                break;
            case "net.minecraft.inventory.ContainerRepair$2":
                if (Config.noAnvilTax) transformAnvilSlot(ctx);
                break;
            case "net.minecraft.server.MinecraftServer":
                if (Config.enableTPSChange) transformServer(ctx);
                break;
            case "net.minecraft.server.management":
                transformPlayerChunkMap(ctx);
                break;
            case "org.apache.logging.log4j.core.lookup.JndiLookup":
                transformLOG4J2(ctx);
                break;
        }
    }

    private static void transformLOG4J2(Context ctx) {
        ConstantData data = ctx.getData();
        int i = data.getMethodByName("lookup");
        Method mn = new Method(data, data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        InsnList insn = mn.code.instructions;
        insn.clear();
        insn.add(new LdcInsnNode(new CstString("JNDI功能已关闭")));
        insn.add(new NPInsnNode(Opcodes.ARETURN));
    }

    private static void transformAnvilSlot(Context ctx) {
        ConstantData data = ctx.getData();
        int i = data.getMethodByName("func_190901_a");
        Method mn = new Method(data, data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        InsnList insn = mn.code.instructions;
        for (i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name.equals("func_82242_a")) {
                    node1.code = Opcodes.INVOKESTATIC;
                    node1.owner = "ilib/asm/util/MCHooks";
                    node1.name = "playerAnvilClick";
                    node1.setParameters("(Lnet/minecraft/entity/player/EntityPlayer;I)V");
                    break;
                }
            }
        }
    }

    private static void transformPlayerChunkMap(Context ctx) {
        ConstantData data = ctx.getData();
        int i = data.getMethodByName("func_72693_b");
        Method mn = new Method(data, data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        int j = 0;
        InsnList insn = mn.code.instructions;
        for (i = 0; i < insn.size(); i++) {
            if (j == 2)
                break;

            InsnNode node = insn.get(i);
            switch (node.getOpcode()) {
                case Opcodes.LDC2_W: {
                    LdcInsnNode node1 = (LdcInsnNode) node;
                    if (node1.c.type() == Constant.LONG && ((CstLong) node1.c).value == 50000000L) {
                        insn.set(i, new FieldInsnNode(Opcodes.GETSTATIC, "ilib/Config.maxChunkTimeTick:J"));
                        j++;
                    }
                }
                break;
                case Opcodes.BIPUSH: {
                    IIndexInsnNode node1 = (IIndexInsnNode) node;
                    if (node1.getIndex() == 49) {
                        insn.set(i, new FieldInsnNode(Opcodes.GETSTATIC, "ilib/Config.maxChunkTick:I"));
                        j++;
                    }
                }
                break;
            }
        }
        if(i != 2)
            logger.error("[PlayerChunkMap.Transform] Node not found!");
    }

    private static void transformServer(Context ctx) {
        ConstantData data = ctx.getData();
        int i = data.getMethodByName("run");
        Method mn = new Method(data, data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        InsnList insn = mn.code.instructions;
        for (i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.getOpcode() == Opcodes.LDC2_W) {
                LdcInsnNode node1 = (LdcInsnNode) node;
                if (node1.c.type() == Constant.LONG && ((CstLong) node1.c).value == 50L) {
                    insn.set(i, new FieldInsnNode(Opcodes.GETSTATIC, "ilib/asm/transformers/Transformer.MSpT:J"));
                    return;
                }
            }
        }
        logger.error("[TPSChange.Transform] Node not found!");
    }

    private static Method beginLoading(Method method) {
        InsnList insn = method.code.instructions;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name.equals("beginMinecraftLoading")) {
                    insn.add(i, new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/ImpLib", "onPreInitDone", "()V"));
                    logger.debug("beginLoad: done.");
                    break;
                }
            }
        }
        return method;
    }

    public static void redirectGC() {
        if (Config.changeWorldSpeed < 3)
            System.gc();
        MinecraftForge.EVENT_BUS.post(new ClientChangeWorldEvent());
    }

    /**
     * 快速世界加载，标题，客户端加载完毕事件，搜索树
     */
    private static void transformMinecraft(Context ctx) {
        ConstantData data = ctx.getData();
        if (!Config.title.equals("Minecraft 1.12.2")) {
            boolean found = false;
            List<Constant> array = data.cp.array();
            for (int i = 0; i < array.size(); i++) {
                Constant c = array.get(i);
                if (c.type() == Constant.UTF) {
                    CstUTF cstUTF = (CstUTF) c;
                    if ("Minecraft 1.12.2".equals(cstUTF.getString())) {
                        cstUTF.setString(Config.title);
                        found = true;
                        break;
                    }
                }
            }
            if (!found)
                logger.error("没有找到MC Title标记!");
        }
        /*List<CstClass> cst = ctx.getClassConstants();
        for (int i = 0; i < cst.size(); i++) {
            CstClass clz = cst.get(i);
            if (clz.getValue().getString().equals("net/minecraft/client/util/SearchTree")) {
                clz.setValue(data.cp.getUtf("ilib/asm/nixim/FastSearchTree"));
                logger.debug("FastSearchTree: done.");
                break;
            }
        }*/
        List<CstRef> methodRef = ctx.getMethodConstants();
        for (int i = 0; i < methodRef.size(); i++) {
            CstRef ref = methodRef.get(i);
            if (ref.getClassName().equals("java/lang/System") && ref.desc().getName().getString().equals("gc")) {
                ref.setClazz(data.cp.getClazz("ilib/asm/transformers/Transformer"));
                ref.desc(data.cp.getDesc("redirectGC", "()V"));
                logger.debug("redirectGC: done.");
                break;
            }
        }
        for (int i = 0; i < data.methods.size(); i++) {
            MethodSimple method = data.methods.get(i);
            if ("func_71384_a".equals(method.name.getString())) {
                data.methods.set(i, Helpers.cast(beginLoading(new Method(data, method))));
                break;
            }
        }
    }

    /**
     * 矿车只坐人
     */
    private static void transformMinecart(Context ctx) {
        ConstantData data = ctx.getData();
        int i = data.getMethodByName("func_180460_a");
        Method mn = new Method(data, data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        InsnList list = mn.code.instructions;
        final IntBiMap<InsnNode> pc = list.getPCMap();

        i = 420;
        while (i < 440) {
            InsnNode node = pc.get(i++);
            if (node != null && node.getOpcode() == Opcodes.INSTANCEOF) {
                ClassInsnNode node1 = (ClassInsnNode) node;
                node1.owner = "net/minecraft/entity/player/EntityPlayer";

                logger.debug("'EntityMinecart' transformed.");
                return;
            }
        }
        logger.error("'EntityMinecart' transform failed.");
        logger.error(mn.code.toString());
        throw new RuntimeException("出错了");
    }

    public static Set<Entity> createEntityList() {
        return new WeakHashSet<>();
    }

    /**
     * 客户端实体列表
     */
    private static void transformWorldClient(Context ctx) {
        ConstantData data = ctx.getData();
        int i = data.getMethodByName("<init>");
        Method mn = new Method(data, data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        InsnList list = mn.code.instructions;

        i = 4;
        do {
            InsnNode node = list.get(i++);
            if (node.getOpcode() == Opcodes.INVOKESTATIC) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name.equals("newHashSet")) {
                    node1.rawDesc("ilib/asm/transformers/Transformer.createEntityList:()Ljava/util/Set;");
                    logger.debug("'WorldClient' transformed.");
                    return;
                }
            }
        } while (i <= 20);
        logger.info("'WorldClient' transform failed.");
    }
}