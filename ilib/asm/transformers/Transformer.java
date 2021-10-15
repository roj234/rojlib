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

import ilib.Config;
import ilib.api.IFasterClassTransformer;
import ilib.asm.Loader;
import ilib.client.api.ChangeWorldEvent;
import org.apache.logging.log4j.Logger;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstLong;
import roj.asm.cst.CstType;
import roj.asm.cst.CstUTF;
import roj.asm.tree.Clazz;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.insn.*;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.util.InsnList;
import roj.collect.IntBiMap;
import roj.collect.WeakHashSet;
import roj.util.ByteList;
import roj.util.Helpers;

import net.minecraft.entity.Entity;

import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public class Transformer implements IFasterClassTransformer {
    protected static Logger logger = Loader.logger();

    public static long MSpT = 50L;

    public ByteList transform(String name, String trName, ByteList data) {
        switch (trName) {
            case "net.minecraft.client.multiplayer.WorldClient":
                return Config.replaceEntityList ? transformWC(data) : data;
            case "net.minecraft.entity.item.EntityMinecart":
                return Config.fixMinecart ? transformMinecart(data) : data;
            case "net.minecraft.client.Minecraft":
                return transformMc(data);
            case "net.minecraft.inventory.ContainerRepair$2":
                return Config.noAnvilTax ? transformAnvilSlot(data) : data;
            case "net.minecraft.server.MinecraftServer":
                return Config.enableTPSChange ? transformServer(data) : data;
            case "net.minecraft.server.management":
                return transformPlayerChunkMap(data);
        }
        /*if(trName.contains(".Entity")) {
            return transformEntityDataManager(data);
        }*/

        return data;
    }

    private static ByteList transformAnvilSlot(ByteList basicClass) {
        ConstantData data = Parser.parseConstants(basicClass);
        int index = data.getMethodByName("func_190901_a");
        Method mn = new Method(data, data.methods.get(index));
        data.methods.set(index, Helpers.cast(mn));

        InsnList insn = mn.code.instructions;
        for (int i = 0; i < insn.size(); i++) {
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

        return Parser.toByteArrayShared(data);
    }

    private static ByteList transformPlayerChunkMap(ByteList basicClass) {
        ConstantData data = Parser.parseConstants(basicClass);
        int index = data.getMethodByName("func_72693_b");
        Method mn = new Method(data, data.methods.get(index));
        data.methods.set(index, Helpers.cast(mn));
        int i = 0;
        for (ListIterator<InsnNode> itr = mn.code.instructions.listIterator(); itr.hasNext(); ) {
            if (i == 2)
                break;

            InsnNode node = itr.next();
            switch (node.getOpcode()) {
                case Opcodes.LDC2_W: {
                    LoadConstInsnNode node1 = (LoadConstInsnNode) node;
                    if (node1.c.type() == CstType.LONG && ((CstLong) node1.c).value == 50000000L) {
                        itr.set(new FieldInsnNode(Opcodes.GETSTATIC, "ilib/Config.maxChunkTimeTick:J"));
                        i++;
                    }
                }
                break;
                case Opcodes.BIPUSH: {
                    IIndexInsnNode node1 = (IIndexInsnNode) node;
                    if (node1.getIndex() == 49) {
                        itr.set(new FieldInsnNode(Opcodes.GETSTATIC, "ilib/Config.maxChunkTick:I"));
                        i++;
                    }

                }
                break;
            }
        }
        if(i != 2)
            logger.error("[PlayerChunkMap.Transform] Node not found!");
        return Parser.toByteArrayShared(data);
    }

    private static ByteList transformServer(ByteList basicClass) {
        ConstantData data = Parser.parseConstants(basicClass);
        int index = data.getMethodByName("run");
        Method mn = new Method(data, data.methods.get(index));
        data.methods.set(index, Helpers.cast(mn));
        int i = 0;
        for (ListIterator<InsnNode> itr = mn.code.instructions.listIterator(); itr.hasNext(); ) {
            InsnNode node = itr.next();
            if (node.getOpcode() == Opcodes.LDC2_W) {
                LoadConstInsnNode node1 = (LoadConstInsnNode) node;
                if (node1.c.type() == CstType.LONG && ((CstLong) node1.c).value == 50L) {
                    itr.set(new FieldInsnNode(Opcodes.GETSTATIC, "ilib/asm/transformers/Transformer.MSpT:J"));
                    return Parser.toByteArrayShared(data);
                }
            }
        }
        logger.error("[TPSChange.Transform] Node not found!");

        return basicClass;
    }

    private Method appendOnEndMark(Method method) {
        for (ListIterator<InsnNode> iterator = method.code.instructions.listIterator(); iterator.hasNext(); ) {
            InsnNode node = iterator.next();
            if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name.equals("beginMinecraftLoading")) {
                    iterator.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/ImpLib", "onPreInitDone", "()V"));
                    logger.debug("Found beginLoad mark");
                    break;
                }
            }
        }
        return method;
    }

    private Method optionalTree(Method method) {
        for (ListIterator<InsnNode> iterator = method.code.instructions.listIterator(method.code.instructions.size()); iterator.hasPrevious(); ) {
            InsnNode node = iterator.previous();
            switch (node.getOpcode()) {
                case Opcodes.NEW: {
                    ClassInsnNode node1 = (ClassInsnNode) node;
                    if (node1.owner.equals("net/minecraft/client/util/SearchTree")) {
                        node1.owner = "ilib/asm/nixim/FastSearchTree";
                        logger.debug("Found Tree New");
                    }
                }
                break;
                case Opcodes.INVOKESPECIAL: {
                    InvokeInsnNode node1 = (InvokeInsnNode) node;
                    if (node1.owner.equals("net/minecraft/client/util/SearchTree") && node1.name.equals("<init>")) {
                        node1.owner("ilib/asm/nixim/FastSearchTree");
                        logger.debug("Found Tree Init");
                        break;
                    }
                }
                break;
            }
        }
        return method;
    }

    private Method optionalGC(Method method) {
        for (ListIterator<InsnNode> iterator = method.code.instructions.listIterator(method.code.instructions.size()); iterator.hasPrevious(); ) {
            InsnNode node = iterator.previous();
            if (node.getOpcode() == Opcodes.INVOKESTATIC) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.owner.equals("java/lang/System") && node1.name.equals("gc")) {
                    node1.rawDesc("ilib/asm/transformers/Transformer.redirectGC:()V");
                    logger.debug("Found GC mark");
                    break;
                }
            }
        }
        return method;
    }

    public static void redirectGC() {
        if (Config.changeWorldSpeed < 3)
            System.gc();
        MinecraftForge.EVENT_BUS.post(new ChangeWorldEvent());
    }

    private ByteList transformMc(ByteList basicClass) {
        ConstantData data = Parser.parseConstants(basicClass);
        if (!Config.title.equals("Minecraft 1.12.2")) {
            boolean found = false;
            List<Constant> array = data.cp.array();
            for (int i = 0; i < array.size(); i++) {
                Constant c = array.get(i);
                if (c.type() == CstType.UTF) {
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
        int found = 0;
        for (ListIterator<MethodSimple> iterator = data.methods.listIterator(); iterator.hasNext(); ) {
            MethodSimple method = iterator.next();
            switch (method.name.getString()) {
                case "func_71384_a": {

                    iterator.set(Helpers.cast(appendOnEndMark(new Method(data, method))));
                    found++;
                }
                break;
                case "func_71353_a": {
                    iterator.set(Helpers.cast(optionalGC(new Method(data, method))));
                    found++;
                }
                break;
                /*case "func_193986_ar": {
                    iterator.set(Helpers.cast(optionalTree(new Method(data, method))));
                    found++;
                }
                break;*/
            }
            if (found == 2) break;
        }
        return Parser.toByteArrayShared(data);
    }

    private static ByteList transformMinecart(ByteList basicClass) {
        logger.debug("Changing class net.minecraft.entity.item.EntityMinecart");
        Clazz cn = Parser.parse(basicClass);
        int index = cn.getMethodByName("func_180460_a");
        Method mn = cn.methods.get(index);

        InsnList list = mn.code.instructions;
        final IntBiMap<InsnNode> pc = list.getPCMap();

        int i = 420;

        while (i < 440) {
            InsnNode node = pc.get(i++);
            if (node != null && node.getOpcode() == Opcodes.INSTANCEOF) {
                ClassInsnNode node1 = (ClassInsnNode) node;
                node1.owner("net/minecraft/entity/player/EntityPlayer");

                logger.debug("net.minecraft.entity.item.EntityMinecart change successful.");
                return Parser.toByteArrayShared(cn);
            }
        }
        logger.error("net.minecraft.entity.item.EntityMinecart change failed.");
        logger.error(mn.toString());
        throw new RuntimeException("出错了");
    }

    public static Set<Entity> createEntityList() {
        return new WeakHashSet<>();
    }

    /**
     * 客户端实体列表
     */
    private static ByteList transformWC(ByteList basicClass) {
        logger.debug("Changing class net/minecraft/client/multiplayer/WorldClient");
        Clazz cn = Parser.parse(basicClass);

        for (Method mn : cn.methods) {
            if (mn.name.equals("<init>")) {
                InsnList list = mn.code.instructions;

                int i = 14;

                do {
                    InsnNode node = list.get(i++);
                    if (node instanceof InvokeInsnNode) {
                        InvokeInsnNode node1 = (InvokeInsnNode) node;
                        node.setOpcode(Opcodes.INVOKESTATIC);
                        node1.rawDesc("ilib/asm/transformers/Transformer.createEntityList:()Ljava/util/Set;");
                        //System.out.println("Target node: " + node);
                        logger.debug("net/minecraft/client/multiplayer/WorldClient change successful.");
                        return Parser.toByteArrayShared(cn);
                    }
                } while (i <= 16);
                logger.info("net/minecraft/client/multiplayer/WorldClient change failed.");
                logger.error(mn.toString());
            }
        }
        throw new RuntimeException("出错了");
    }
}