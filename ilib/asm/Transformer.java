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
import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.*;
import roj.collect.IntBiMap;
import roj.io.IOUtil;
import roj.text.SimpleLineReader;
import roj.util.Helpers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static ilib.asm.Loader.logger;

/**
 * 标准(ASM)class转换器
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class Transformer implements ContextClassTransformer {
    @Override
    public void transform(String trName, Context ctx) {
        switch (trName) {
            case "net.minecraft.util.math.MathHelper":
                MathOptimizer.optimizeMathHelper(ctx);
                break;
            case "net.minecraft.client.multiplayer.WorldClient":
                if (Config.replaceEntityList || Config.noShitSound)
                    transformWorldClient(ctx);
                break;
            case "net.minecraft.entity.item.EntityMinecart":
                if (Config.fixMinecart) onlySteveOnCart(ctx);
                break;
            case "net.minecraft.client.Minecraft":
                transformMinecraft(ctx);
                break;
            case "net.minecraft.inventory.ContainerRepair$2":
                if (Config.noAnvilTax) transformAnvilSlot(ctx);
                break;
            case "net.minecraft.server.MinecraftServer":
                if (Config.enableTPSChange) changeTPS(ctx);
                break;
            case "net.minecraft.server.management":
                transformPlayerChunkMap(ctx);
                break;
            case "org.apache.logging.log4j.core.lookup.JndiLookup":
                transformLOG4J2(ctx);
                break;
            case "net.minecraft.stats.RecipeBookServer":
                if (Config.noRecipeBook) noRecipeBook(ctx);
                break;
            case "net.minecraft.advancements.AdvancementManager":
                if (Config.noAdvancement) noAdvancement(ctx);
                break;
            case "net.minecraft.entity.player.EntityPlayer":
                if (Config.attackCD == 0) noAttackCD(ctx);
                break;
            case "net.minecraft.entity.EntityLivingBase":
                if (Config.noCollision) noEntityCollision(ctx);
                if (Config.fastDismount) fastDismount(ctx);
                break;
            case "net.minecraft.server.management.PlayerInteractionManager":
                if (Config.noGhostBlock) noGhostBlock(ctx);
                break;
            case "net.minecraft.block.BlockPistonBase":
            case "net.minecraft.block.BlockRedstoneWire":
                if (Config.noSoManyBlockPos) {
                    fastEnumFacing(ctx);
                    return;
                }
                break;
            case "net.minecraft.client.network.NetHandlerPlayClient":
                if (Config.separateDismount) transformDismountMessage(ctx);
                break;
            case "net.minecraft.world.WorldServer":
                if (Config.myNextTickList) tickMyHashSet(ctx);
                break;
            case "net.minecraft.client.renderer.OpenGlHelper":
                if (Config.oshi886) oshi886(ctx);
                break;
            case "net.minecraft.util.EnumFacing":
                if (Config.replaceEnumFacing) transformEnumFacing(ctx);
                return;
            case "net.minecraft.client.renderer.RegionRenderCacheBuilder":
                if (Config.smallBuf) transformRRCB(ctx);
                break;
            case "paulscode.sound.libraries.ChannelLWJGLOpenAL":
                if (Config.soundRecycle) transformChannel(ctx);
                break;
            case "net.minecraft.client.renderer.RenderGlobal":
                transformRG(ctx);
                break;
            case "net.minecraft.client.renderer.GLAllocation":
                transformGLAlloc(ctx);
        }
        if (Config.replaceEnumFacing) fastEnumFacing(ctx);
        if (Config.tractDirectMem) traceDirectMemory(ctx);
    }

    private static void transformGLAlloc(Context ctx) {
        List<? extends MethodNode> cp = ctx.getData().methods;
        for (int i = 0; i < cp.size(); i++) {
            MethodNode node = cp.get(i);
            node.accessFlag(node.accessFlag() & ~AccessFlag.SUPER_OR_SYNC);
        }
    }


    private static void transformRG(Context ctx) {
        ConstantPool cp = ctx.getData().cp;
        List<Constant> cref = cp.getConstants();
        for (int i = 0; i < cref.size(); i++) {
            Constant c = cref.get(i);
            if (c.type() == Constant.INT) {
                CstInt c1 = (CstInt) c;
                int v = c1.value;
                if (v == 69696) {
                    c1.value = 256;
                    break;
                }
            }
        }
    }

    private static void traceDirectMemory(Context ctx) {
        ConstantData data = ctx.getData();
        List<CstRef> methods = ctx.getMethodConstants();
        for (int i = 0; i < methods.size(); i++) {
            CstRef ref = methods.get(i);
            if (ref.getClassName().equals("org/lwjgl/BufferUtils")) {
                if (ref.desc().getName().getString().equals("createByteBuffer")) {
                    ref.setClazz(data.cp.getClazz("ilib/asm/Transformer"));
                }
            } else if (ref.getClassName().equals("java/nio/ByteBuffer")) {
                if (ref.desc().getName().getString().startsWith("allocateDirect")) {
                    ref.setClazz(data.cp.getClazz("ilib/asm/Transformer"));
                }
            }
        }
    }

    public static ByteBuffer allocateDirect(int size) {
        Loader.logger.info("allocateDirect(" + size + ")", new Throwable());
        return ByteBuffer.allocateDirect(size);
    }

    public static ByteBuffer createByteBuffer(int size) {
        Loader.logger.info("createByteBuffer(" + size + ")", new Throwable());
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private static void transformChannel(Context ctx) {
        ConstantData data = ctx.getData();
        List<CstClass> clz = ctx.getClassConstants();
        for (int i = 0; i < clz.size(); i++) {
            CstClass ref = clz.get(i);
            if (ref.getValue().getString().equals("org/lwjgl/BufferUtils")) {
                ref.getValue().setString("ilib/asm/util/MCReplaces");
            }
        }
    }

    private static void transformRRCB(Context ctx) {
        ConstantPool cp = ctx.getData().cp;
        List<Constant> cref = cp.getConstants();
        for (int i = 0; i < cref.size(); i++) {
            Constant c = cref.get(i);
            if (c.type() == Constant.INT) {
                CstInt c1 = (CstInt) c;
                int v = c1.value;
                switch (v) {
                    case 2097152:
                        c1.value = 131072;
                        break;
                    case 131072:
                    case 262144:
                        c1.value = 65536;
                        break;
                }
            }
        }
    }

    private static void transformEnumFacing(Context ctx) {
        fastEnumFacing(ctx);

        Method m = ctx.getData().getUpgradedMethod("<clinit>");
        InsnList insn = m.code.instructions;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.nodeType() == InsnNode.T_INVOKE) {
                InvokeInsnNode iin = (InvokeInsnNode) node;
                if (iin.owner.equals("ilib/misc/MCHooks"))
                iin.owner = "net/minecraft/util/EnumFacing";
            }
        }
    }

    private static void oshi886(Context ctx) {
        ConstantPool cp = ctx.getData().cp;
        List<CstClass> cref = ctx.getClassConstants();
        for (int i = 0; i < cref.size(); i++) {
            CstClass c = cref.get(i);
            if (c.getValue().getString().equals("oshi/SystemInfo")) {
                // 会报NoSuchMethodError, 不过它在一个try-catch里
                c.getValue().setString("java/lang/Object");
                break;
            }
        }
    }

    private static void tickMyHashSet(Context ctx) {
        ConstantPool cp = ctx.getData().cp;
        List<CstRef> mref = ctx.getMethodConstants();
        for (int i = 0; i < mref.size(); i++) {
            CstRef m = mref.get(i);
            if (m.desc().getName().getString().equals("newHashSet")) {
                m.setClazz(cp.getClazz("roj/util/Helpers"));
                m.desc(cp.getDesc("newMyHashSet", "()Lroj/collect/MyHashSet;"));
                break;
            }
        }
    }

    private static void transformDismountMessage(Context ctx) {
        List<CstRef> mref = ctx.getMethodConstants();
        for (int i = 0; i < mref.size(); i++) {
            CstRef m = mref.get(i);
            // I18n.format
            if (m.desc().getName().getString().equals("func_135052_a")) {
                // 只有一处引用，所以不用data.cp.newXXX
                m.getClazz().getValue().setString("ilib/misc/MCHooks");
                m.desc().getName().setString("dismountMessage");
                break;
            }
        }
    }

    private static void transformLOG4J2(Context ctx) {
        ConstantData data = ctx.getData();
        Method mn = data.getUpgradedMethod("lookup");

        InsnList insn = mn.code.instructions;
        insn.clear();
        insn.add(new LdcInsnNode(new CstString("JNDI功能已关闭")));
        insn.add(new NPInsnNode(Opcodes.ARETURN));
    }

    private static void transformAnvilSlot(Context ctx) {
        ConstantData data = ctx.getData();
        Method mn = data.getUpgradedMethod("func_190901_a");

        InsnList insn = mn.code.instructions;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name.equals("func_82242_a")) {
                    node1.code = Opcodes.INVOKESTATIC;
                    node1.owner = "ilib/misc/MCHooks";
                    node1.name = "playerAnvilClick";
                    node1.rawDesc("(Lnet/minecraft/entity/player/EntityPlayer;I)V");
                    break;
                }
            }
        }
    }

    private static void transformPlayerChunkMap(Context ctx) {
        ConstantData data = ctx.getData();
        Method mn = data.getUpgradedMethod("func_72693_b");

        int i, j = 0;
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

    private static void changeTPS(Context ctx) {
        ConstantData data = ctx.getData();
        Method mn = data.getUpgradedMethod("run");

        InsnList insn = mn.code.instructions;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.getOpcode() == Opcodes.LDC2_W) {
                LdcInsnNode node1 = (LdcInsnNode) node;
                if (node1.c.type() == Constant.LONG && ((CstLong) node1.c).value == 50L) {
                    insn.set(i, new FieldInsnNode(Opcodes.GETSTATIC, "ilib/misc/MCHooks.MSpT:J"));
                }
            }
        }
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
                        cstUTF.setString(Config.title.equals("random") ? randomTitle() : Config.title);
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
        if (Config.changeWorldSpeed == 3) {
            List<CstRef> methodRef = ctx.getMethodConstants();
            for (int i = 0; i < methodRef.size(); i++) {
                CstRef ref = methodRef.get(i);
                if (ref.getClassName().equals("java/lang/System") && ref.desc().getName().getString().equals("gc")) {
                    ref.setClazz(data.cp.getClazz("ilib/misc/MCHooks"));
                    ref.desc(data.cp.getDesc("empty", "()V"));
                    logger.debug("redirectGC: done.");
                    break;
                }
            }
        }
    }

    private static String randomTitle() {
        SimpleLineReader slr;
        try {
            slr = new SimpleLineReader(IOUtil.readUTF("assets/minecraft/texts/splashes.txt"));
            slr.skipLines((int) System.currentTimeMillis() % slr.size() - 1);
            return "我的世界 1.12.2 —— " + slr.next();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return "随机标题读取失败 :(";
    }

    private static Method beginLoading(Method method) {
        InsnList insn = method.code.instructions;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                InvokeInsnNode node1 = (InvokeInsnNode) node;
                if (node1.name.equals("beginMinecraftLoading")) {
                    insn.add(i+1, new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/ImpLib", "onPreInitDone", "()V"));
                    logger.debug("beginLoad: done.");
                    break;
                }
            }
        }
        return method;
    }

    /**
     * 矿车只坐人
     */
    private static void onlySteveOnCart(Context ctx) {
        ConstantData data = ctx.getData();
        Method mn = data.getUpgradedMethod("func_180460_a");

        InsnList list = mn.code.instructions;
        final IntBiMap<InsnNode> pc = list.getPCMap();

        int i = 420;
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

    /**
     * 客户端实体列表
     */
    private static void transformWorldClient(Context ctx) {
        ConstantData data = ctx.getData();

        _trans:
        if (Config.replaceEntityList) {
            Method mn = data.getUpgradedMethod("<init>");

            InsnList list = mn.code.instructions;

            int i = 4;
            do {
                InsnNode node = list.get(i++);
                if (node.getOpcode() == Opcodes.INVOKESTATIC) {
                    InvokeInsnNode node1 = (InvokeInsnNode) node;
                    if (node1.name.equals("newHashSet")) {
                        node1.fullDesc("roj/collect/WeakHashSet.newWeakHashSet:()Lroj/collect/WeakHashSet;");
                        break _trans;
                    }
                }
            } while (i <= 20);
            logger.warn("replaceEntityList failed.");
        }

        if (Config.noShitSound) {
            // playMoodSoundAndCheckLight
            int i = data.getMethodByName("func_147467_a");
            data.methods.remove(i);
        }
    }

    private static void noRecipeBook(Context ctx) {
        ConstantData data = ctx.getData();

        // sendPacket
        replaceWithEmpty(data, "func_194081_a");
        // read
        replaceWithEmpty(data, "func_192825_a");

        Method write = replaceWithEmpty(data, "func_192824_e");
        AttrCode code = write.code;
        code.stackSize = 2;
        InsnList list = code.instructions;
        list.clear();
        list.add(new ClassInsnNode(Opcodes.NEW, "net/minecraft/nbt/NBTTagCompound"));
        list.add(NPInsnNode.of(Opcodes.DUP));
        list.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/nbt/NBTTagCompound", "<init>", "()V"));
        list.add(NPInsnNode.of(Opcodes.ARETURN));
    }

    private static void noAdvancement(Context ctx) {
        ConstantData data = ctx.getData();

        // reload
        replaceWithEmpty(data, "func_192779_a");
    }

    private static void noAttackCD(Context ctx) {
        ConstantData data = ctx.getData();

        Method getCooledAttackStrength = replaceWithEmpty(data, "func_184825_o");
        AttrCode code = getCooledAttackStrength.code;
        code.stackSize = 1;
        InsnList list = code.instructions;
        list.clear();
        list.add(NPInsnNode.of(Opcodes.FCONST_1));
        list.add(NPInsnNode.of(Opcodes.FRETURN));
    }

    private static void noEntityCollision(Context ctx) {
        ConstantData data = ctx.getData();

        // collideWithNearbyEntities
        replaceWithEmpty(data, "func_85033_bc");
    }

    private static void fastDismount(Context ctx) {
        ConstantData data = ctx.getData();

        Method dismountEntity = replaceWithEmpty(data, "func_110145_l");
        AttrCode code = dismountEntity.code;
        code.stackSize = 2;
        InsnList list = code.instructions;
        list.clear();
        list.add(NPInsnNode.of(Opcodes.ALOAD_0));
        list.add(NPInsnNode.of(Opcodes.ALOAD_1));
        list.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "ilib/util/EntityHelper", "dismountEntity",
                                    "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/Entity;)V"));
        list.add(NPInsnNode.of(Opcodes.RETURN));
    }

    private static void noGhostBlock(Context ctx) {
        ConstantData data = ctx.getData();

        // onBlockClicked
        int i = data.getMethodByName("func_180784_a");
        Method mn = new Method(data, (MethodSimple) data.methods.get(i));
        data.methods.set(i, Helpers.cast(mn));

        AttrCode code = mn.code;
        InsnList list = code.instructions;
        InsnNode ARETURN = list.remove(list.size() - 1);
        // this.player.connection.sendPacket(new SPacketBlockChange(world, pos));
        list.add(NPInsnNode.of(Opcodes.ALOAD_0));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/server/management/PlayerInteractionManager",
                                   "field_73090_b", new Type("net/minecraft/entity/player/EntityPlayerMP")));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/entity/player/EntityPlayerMP",
                                   "field_71135_a", new Type("net/minecraft/network/NetHandlerPlayServer")));
        list.add(new ClassInsnNode(Opcodes.NEW, "net/minecraft/network/play/server/SPacketBlockChange"));
        list.add(NPInsnNode.of(Opcodes.DUP));
        list.add(NPInsnNode.of(Opcodes.ALOAD_0));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/server/management/PlayerInteractionManager",
                                   "field_73092_a", new Type("net/minecraft/world/World")));
        list.add(NPInsnNode.of(Opcodes.ALOAD_1));
        list.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/network/play/server/SPacketBlockChange",
                                    "<init>", "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"));
        list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/network/NetHandlerPlayServer",
                                    "func_147359_a", "(Lnet/minecraft/network/Packet;)V"));
        list.add(ARETURN);
    }

    // Utilities

    private static void fastEnumFacing(Context ctx) {
        ConstantData data = ctx.getData();
        List<CstRef> methods = ctx.getMethodConstants();
        for (int i = 0; i < methods.size(); i++) {
            CstRef ref = methods.get(i);
            if (ref.matches("net/minecraft/util/EnumFacing", "values", "()[Lnet/minecraft/util/EnumFacing;")) {
                ref.setClazz(data.cp.getClazz("ilib/misc/MCHooks"));
            } else if (ref.matches("net/minecraft/util/BlockRenderLayer", "values", "()[Lnet/minecraft/util/BlockRenderLayer;")) {
                ref.setClazz(data.cp.getClazz("ilib/asm/util/MCReplaces"));
            }
        }
    }

    private static Method replaceWithEmpty(ConstantData data, String name) {
        int i = data.getMethodByName(name);

        MethodSimple ms = (MethodSimple) data.methods.get(i);
        Method mn = new Method(ms.accesses, data, ms.name.getString(), ms.rawDesc());
        Object code = ms.attributes().getByName("Code");
        if (code != null) {
            AttrCode c = mn.code = new AttrCode(ms);
            c.localSize = (char) ParamHelper.paramSize(ms.rawDesc());
            if ((AccessFlag.STATIC & mn.accesses) == 0) c.localSize++;
            c.instructions.add(NodeHelper.X_RETURN(ms.getReturnType().nativeName()));
        }
        data.methods.set(i, Helpers.cast(mn));
        return mn;
    }
}