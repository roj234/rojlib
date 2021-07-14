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
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstLong;
import roj.asm.cst.CstType;
import roj.asm.cst.CstUTF;
import roj.asm.struct.Clazz;
import roj.asm.struct.ConstantData;
import roj.asm.struct.Method;
import roj.asm.struct.insn.*;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.util.InsnList;
import roj.collect.IntBiMap;
import roj.collect.WeakHashSet;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            case "ilib.AT_PATCH_AT":
                return transformAT(data);/*
            case "net.minecraft.item.crafting.FurnaceRecipes":
                return Config.fastRecipe ? transformFR(data) : data;*/
            case "net.minecraft.server.MinecraftServer":
                return Config.enableTPSChange ? transformServer(data) : data;
            //case "net.minecraft.client.particle.ParticleManager":
            //    return Config.maxParticleCountPerLayer == 16384 ? data : transformParticleManager(data);
            case "net.minecraft.server.management":
                return transformPlayerChunkMap(data);
        }
        /*if(trName.contains(".Entity")) {
            return transformEntityDataManager(data);
        }*/

        return data;
    }

    private ByteList transformPlayerChunkMap(ByteList basicClass) {
        ConstantData data = Parser.parseConstants(basicClass);
        for (ListIterator<MethodSimple> iterator = data.methods.listIterator(); iterator.hasNext(); ) {
            MethodSimple method = iterator.next();
            if (method.name.getString().equals("func_72693_b")) {
                iterator.set(Helpers.cast(replaceMethodTick(new Method(data, method))));
                break;
            }
        }
        return Parser.toByteArrayShared(data);
    }

    private Method replaceMethodTick(Method method) {
        int i = 0;
        for (ListIterator<InsnNode> itr = method.code.instructions.listIterator(); itr.hasNext(); ) {
            if (i == 2)
                return method;

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
        logger.error("Node not found!");
        return method;
    }

    private byte[] transformEntityDataManager(byte[] basicClass) {
        Clazz clazz = Parser.parse(basicClass, 0);
        /*for(Method method : clazz.methods) {
            if(method.name.equals("")) {

            }
        }*/
        return basicClass;
    }

    private ByteList transformServer(ByteList basicClass) {
        ConstantData data = Parser.parseConstants(basicClass);
        for (ListIterator<MethodSimple> iterator = data.methods.listIterator(); iterator.hasNext(); ) {
            MethodSimple method = iterator.next();
            if (method.name.getString().equals("run")) {
                iterator.set(Helpers.cast(replaceServerSleep(new Method(data, method))));
                break;
            }
        }
        return Parser.toByteArrayShared(data);
    }

    private Method replaceServerSleep(Method method) {
        int i = 0;
        for (ListIterator<InsnNode> itr = method.code.instructions.listIterator(); itr.hasNext(); ) {
            InsnNode node = itr.next();
            if (node.getOpcode() == Opcodes.LDC2_W) {
                LoadConstInsnNode node1 = (LoadConstInsnNode) node;
                if (node1.c.type() == CstType.LONG && ((CstLong) node1.c).value == 50L) {
                    itr.set(new FieldInsnNode(Opcodes.GETSTATIC, "ilib/asm/transformers/Transformer.MSpT:J"));
                    return method;
                }
            }
        }
        logger.error("TPS change node not found!");
        return method;
    }

    private ByteList transformAT(ByteList basicClass) {
        String old = "ilib/AT_PATCH_AT\\$Modifier";
        String now = "net/minecraftforge/fml/common/asm/transformers/AccessTransformer\\$Modifier";

        Matcher matcher = Pattern.compile(old, Pattern.LITERAL).matcher("");

        ConstantData data = Parser.parseConstants(basicClass);
        for (Constant constant : data.cp.array()) {
            if (constant != null && constant.type() == CstType.UTF) {
                CstUTF cstUTF = (CstUTF) constant;
                if (cstUTF.getString().length() >= old.length()) {
                    String nn = matcher.reset(cstUTF.getString()).replaceAll(now);
                    if (!nn.equals(cstUTF.getString()))
                        System.out.println("Changed: " + cstUTF.getString() + " -> " + nn);
                    cstUTF.setString(nn);
                }
            }
        }

        return Parser.toByteArrayShared(data);
    }

    private Method appendOnEndMark(Method method) {
        for (ListIterator<InsnNode> iterator = method.code.instructions.listIterator(); iterator.hasNext(); ) {
            InsnNode node = iterator.next();
            if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name().equals("beginMinecraftLoading")) {
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
                    if (node1.owner().equals("net/minecraft/client/util/SearchTree")) {
                        node1.owner("ilib/asm/nixim/FastSearchTree");
                        logger.debug("Found Tree New");
                    }
                }
                break;
                case Opcodes.INVOKESPECIAL: {
                    InvokeInsnNode node1 = (InvokeInsnNode) node;
                    if (node1.owner().equals("net/minecraft/client/util/SearchTree") && node1.name().equals("<init>")) {
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
                if (node1.owner().equals("java/lang/System") && node1.name().equals("gc")) {
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
            for (Constant constant : data.cp.array()) {
                if (constant != null && constant.type() == CstType.UTF) {
                    CstUTF cstUTF = (CstUTF) constant;
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
        Clazz cn = Parser.parse(basicClass, 0);

        for (Method mn : cn.methods) {
            // find srg
            if (mn.name.equals("func_180460_a")) {
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
            }
        }
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
        Clazz cn = Parser.parse(basicClass, 0);

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