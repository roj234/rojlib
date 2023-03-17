package ilib.asm;

import ilib.Config;
import ilib.api.ContextClassTransformer;
import roj.asm.cst.*;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.util.InsnList;
import roj.asm.util.TransformUtil;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.collect.IntBiMap;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.util.Helpers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;

import static ilib.asm.Loader.logger;
import static roj.asm.Opcodes.*;

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
				if (Config.replaceEntityList || Config.noShitSound) transformWorldClient(ctx);
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
			case "net.minecraft.client.gui.recipebook.GuiRecipeBook":
				if (Config.noRecipeBook) noRecipeBook_client(ctx);
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
			case "net.minecraft.client.renderer.BufferBuilder":
				if (Config.smallBuf) NxBuf2(ctx);
				break;
			case "net.minecraft.client.renderer.GLAllocation":
				transformGLAlloc(ctx);
				break;
			case "net.minecraft.client.particle.ParticleManager":
				if (Config.maxParticleCountPerLayer != 16384) transformParticleCount(ctx);
				break;
			case "net.minecraftforge.event.entity.EntityEvent":
				if (Config.trimEvent) trimEvent_0(ctx);
				break;
			case "net.minecraftforge.event.entity.living.LivingEvent":
				if (Config.trimEvent) trimEvent_1(ctx);
				break;
			case "net.minecraftforge.event.entity.player.PlayerEvent":
				if (Config.trimEvent) trimEvent_2(ctx);
				break;
			case "net.minecraft.client.renderer.entity.RenderLivingBase":
				if (Config.noDeathAnim) noDeathAnim_Red(ctx);
				break;
			case "net.minecraft.client.renderer.EntityRenderer":
				if (Config.noDeathAnim) noDeathAnim_Rotate(ctx);
				break;
			case "net.minecraftforge.fml.common.FMLModContainer":
				betterClassLoadingError(ctx);
				break;
		}
		if (Config.replaceEnumFacing) fastEnumFacing(ctx);
		if (Config.tractDirectMem) traceDirectMemory(ctx);
	}

	private static void betterClassLoadingError(Context ctx) {
		for (CstClass clz : ctx.getClassConstants()) {
			if (clz.getValue().getString().equals("java/lang/ClassNotFoundException")) {
				clz.getValue().setString("java/lang/Throwable");
			}
		}
	}

	private static void noDeathAnim_Red(Context ctx) {
		Method setBrightness = ctx.getData().getUpgradedMethod("func_177092_a");
		InsnList insn = setBrightness.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.getOpcode() == GETFIELD) {
				FieldInsnNode fin = (FieldInsnNode) node;
				if (fin.name.equals("field_70725_aQ")) {
					// 0 > 0 : false
					insn.set(i, NPInsnNode.of(ICONST_0));
					insn.remove(i - 1);
					break;
				}
			}
		}
	}

	private static void noDeathAnim_Rotate(Context ctx) {
		Method hurtCameraEffect = ctx.getData().getUpgradedMethod("func_78482_e");
		InsnList insn = hurtCameraEffect.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.getOpcode() == INVOKEVIRTUAL) {
				InvokeInsnNode iin = (InvokeInsnNode) node;
				if (iin.name.equals("func_110143_aJ")) {
					// 1 <= 0: false
					insn.set(i, NPInsnNode.of(FCONST_1));
					insn.remove(i - 1);
					break;
				}
			}
		}
	}

	private static void trimEvent_0(Context ctx) {
		ConstantData data = ctx.getData();
		data.fields.get(data.getField("entity")).accessFlag(AccessFlag.PUBLIC | AccessFlag.FINAL);
	}

	private static void trimEvent_1(Context ctx) {
		ConstantData data = ctx.getData();
		data.fields.remove(data.getField("entityLiving"));

		Method init = data.getUpgradedMethod("<init>");
		InsnList insn = init.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			if (insn.get(i).getOpcode() == INVOKESPECIAL) {
				insn.removeRange(i + 1, insn.size() - 1);
				break;
			}
		}

		Method getEntityLiving = data.getUpgradedMethod("getEntityLiving");
		insn = getEntityLiving.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			if (insn.get(i).getOpcode() == GETFIELD) {
				FieldInsnNode fin = (FieldInsnNode) insn.get(i);
				fin.owner = "net/minecraftforge/event/entity/EntityEvent";
				fin.name = "entity";
				fin.rawType = "Lnet/minecraft/entity/Entity;";
				insn.add(i + 1, new ClassInsnNode(CHECKCAST, "net/minecraft/entity/EntityLivingBase"));
				break;
			}
		}
	}

	private static void trimEvent_2(Context ctx) {
		ConstantData data = ctx.getData();
		data.fields.remove(data.getField("entityPlayer"));

		Method init = data.getUpgradedMethod("<init>");
		InsnList insn = init.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			if (insn.get(i).getOpcode() == INVOKESPECIAL) {
				insn.removeRange(i + 1, insn.size() - 1);
				break;
			}
		}

		Method getEntityPlayer = data.getUpgradedMethod("getEntityPlayer");
		insn = getEntityPlayer.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			if (insn.get(i).getOpcode() == GETFIELD) {
				FieldInsnNode fin = (FieldInsnNode) insn.get(i);
				fin.owner = "net/minecraftforge/event/entity/EntityEvent";
				fin.name = "entity";
				fin.rawType = "Lnet/minecraft/entity/Entity;";
				insn.add(i + 1, new ClassInsnNode(CHECKCAST, "net/minecraft/entity/player/EntityPlayer"));
				break;
			}
		}
	}

	// compat with OF
	private static void NxBuf2(Context ctx) {
		ConstantPool cp = ctx.getData().cp;
		List<Constant> cref = cp.array();
		for (int i = 0; i < cref.size(); i++) {
			Constant c = cref.get(i);
			if (c.type() == Constant.INT) {
				CstInt c1 = (CstInt) c;
				int v = c1.value;
				if (v == 2097152) {
					c1.value = 262144;
					break;
				}
			}
		}

		Method growBuffer = ctx.getData().getUpgradedMethod("func_181670_b");
		InsnList insn = growBuffer.code.instructions;
		int j = 0;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.getOpcode() == PUTFIELD || node.getOpcode() == GETSTATIC) {
				FieldInsnNode fin = (FieldInsnNode) node;
				if (fin.name.equals("field_187316_a")) {
					insn.removeRange(i, i + 7);
					j |= 1;
					if (j == 3) break;
				} else if (fin.name.equals("field_179001_a")) {
					// aload_0,
					// aload_2
					i -= 2;
					insn.add(i++, NPInsnNode.of(ALOAD_0));
					insn.add(i++, new FieldInsnNode(GETFIELD, fin.owner, fin.name, fin.rawType));
					insn.add(i, new InvokeInsnNode(INVOKESTATIC, "roj/io/NIOUtil", "clean", "(Ljava/nio/Buffer;)V"));
					j |= 2;
					if (j == 3) break;
				}
			}
		}
	}


	private static void transformParticleCount(Context ctx) {
		Method m = ctx.getData().getUpgradedMethod("func_78868_a");
		InsnList insn = m.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node instanceof IIndexInsnNode && ((IIndexInsnNode) node).getIndex() == 16384) {
				((IIndexInsnNode) node).setIndex(Config.maxParticleCountPerLayer);
			}
		}
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
		List<Constant> cref = cp.array();
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
				ref.getValue().setString("ilib/asm/util/MCHooksClient");
			}
		}
	}

	private static void transformRRCB(Context ctx) {
		ConstantPool cp = ctx.getData().cp;
		List<Constant> cref = cp.array();
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
				if (iin.owner.equals("ilib/misc/MCHooks")) iin.owner = "net/minecraft/util/EnumFacing";
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
		CodeWriter cw = replaceWithEmpty(data, "lookup", true);

		cw.ldc(new CstString("JNDI功能已关闭"));
		cw.one(ARETURN);
	}

	private static void transformAnvilSlot(Context ctx) {
		ConstantData data = ctx.getData();
		Method mn = data.getUpgradedMethod("func_190901_a");

		InsnList insn = mn.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.getOpcode() == INVOKEVIRTUAL) {
				InvokeInsnNode node1 = (InvokeInsnNode) node;
				if (node1.name.equals("func_82242_a")) {
					node1.code = INVOKESTATIC;
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
			if (j == 2) break;

			InsnNode node = insn.get(i);
			switch (node.getOpcode()) {
				case LDC2_W: {
					LdcInsnNode node1 = (LdcInsnNode) node;
					if (node1.c.type() == Constant.LONG && ((CstLong) node1.c).value == 50000000L) {
						insn.set(i, new FieldInsnNode(GETSTATIC, "ilib/Config.maxChunkTimeTick:J"));
						j++;
					}
				}
				break;
				case BIPUSH: {
					IIndexInsnNode node1 = (IIndexInsnNode) node;
					if (node1.getIndex() == 49) {
						insn.set(i, new FieldInsnNode(GETSTATIC, "ilib/Config.maxChunkTick:I"));
						j++;
					}
				}
				break;
			}
		}
		if (i != 2) logger.error("[PlayerChunkMap.Transform] Node not found!");
	}

	private static void changeTPS(Context ctx) {
		ConstantData data = ctx.getData();
		Method mn = data.getUpgradedMethod("run");

		InsnList insn = mn.code.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.getOpcode() == LDC2_W) {
				LdcInsnNode node1 = (LdcInsnNode) node;
				if (node1.c.type() == Constant.LONG && ((CstLong) node1.c).value == 50L) {
					insn.set(i, new FieldInsnNode(GETSTATIC, "ilib/misc/MCHooks.MSpT:J"));
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
			if (!found) logger.error("没有找到MC Title标记!");
		}
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
		LineReader slr;
		try {
			slr = new LineReader(IOUtil.readUTF("assets/minecraft/texts/splashes.txt"));
			slr.skipLines(new Random(System.currentTimeMillis()).nextInt(slr.size()));
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
			if (node.getOpcode() == INVOKEVIRTUAL) {
				InvokeInsnNode node1 = (InvokeInsnNode) node;
				if (node1.name.equals("beginMinecraftLoading")) {
					insn.add(i + 1, new InvokeInsnNode(INVOKESTATIC, "ilib/ImpLib", "onPreInitDone", "()V"));
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
			if (node != null && node.getOpcode() == INSTANCEOF) {
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
				if (node.getOpcode() == INVOKESTATIC) {
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
			int i = data.getMethod("func_147467_a");
			data.methods.remove(i);
		}
	}

	private static void noRecipeBook(Context ctx) {
		ConstantData data = ctx.getData();

		// sendPacket
		replaceWithEmpty(data, "func_194081_a");
		// read
		replaceWithEmpty(data, "func_192825_a");

		CodeWriter write = replaceWithEmpty(data, "func_192824_e", true);

		write.visitSize(2, 1);
		write.newObject("net/minecraft/nbt/NBTTagCompound");
		write.one(ARETURN);
		write.finish();
	}

	private static void noRecipeBook_client(Context ctx) {
		ConstantData data = ctx.getData();

		// include <init>
		for (int i = 0; i < data.methods.size(); i++) {
			MethodNode m = data.methods.get(i);
			if (m.rawDesc().endsWith(")V")) {
				CodeWriter init = replaceWithEmpty(data, m.name(), m.name().equals("<init>"));
				if (init != null) {
					init.visitSize(1, 1);
					init.one(ALOAD_0);
					init.invoke(INVOKESPECIAL, data.parent, "<init>", "()V");
					init.one(RETURN);
					init.finish();
				}
			}
		}
		// remove final
		for (int i = 0; i < data.fields.size(); i++) {
			FieldNode n = data.fields.get(i);
			if ((n.accessFlag() & AccessFlag.STATIC) == 0)
				n.accessFlag(n.accessFlag() & ~AccessFlag.FINAL);
		}

		CodeWriter isVisible = replaceWithEmpty(data, "func_191878_b", true);
		isVisible.visitSize(1, 1);
		isVisible.one(ICONST_0);
		isVisible.one(IRETURN);
	}

	private static void noAdvancement(Context ctx) {
		ConstantData data = ctx.getData();

		// reload
		replaceWithEmpty(data, "func_192779_a");
	}

	private static void noAttackCD(Context ctx) {
		ConstantData data = ctx.getData();

		CodeWriter getCooledAttackStrength = replaceWithEmpty(data, "func_184825_o", true);
		getCooledAttackStrength.visitSize(1, 2);
		getCooledAttackStrength.one(FCONST_1);
		getCooledAttackStrength.one(FRETURN);
	}

	private static void noEntityCollision(Context ctx) {
		ConstantData data = ctx.getData();

		// collideWithNearbyEntities
		replaceWithEmpty(data, "func_85033_bc");
	}

	private static void fastDismount(Context ctx) {
		ConstantData data = ctx.getData();

		CodeWriter dismountEntity = replaceWithEmpty(data, "func_110145_l", true);
		dismountEntity.visitSize(2, 2);
		dismountEntity.one(ALOAD_0);
		dismountEntity.one(ALOAD_1);
		dismountEntity.invoke(INVOKESTATIC, "ilib/util/EntityHelper", "dismountEntity", "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/Entity;)V");
		dismountEntity.one(RETURN);
	}

	private static void noGhostBlock(Context ctx) {
		ConstantData data = ctx.getData();

		// onBlockClicked
		int i = data.getMethod("func_180784_a");
		Method mn = new Method(data, (RawMethod) data.methods.get(i));
		data.methods.set(i, Helpers.cast(mn));

		AttrCode code = mn.code;
		InsnList list = code.instructions;
		InsnNode ARETURN = list.remove(list.size() - 1);
		// this.player.connection.sendPacket(new SPacketBlockChange(world, pos));
		list.add(NPInsnNode.of(ALOAD_0));
		list.add(new FieldInsnNode(GETFIELD, "net/minecraft/server/management/PlayerInteractionManager", "field_73090_b", new Type("net/minecraft/entity/player/EntityPlayerMP")));
		list.add(new FieldInsnNode(GETFIELD, "net/minecraft/entity/player/EntityPlayerMP", "field_71135_a", new Type("net/minecraft/network/NetHandlerPlayServer")));
		list.add(new ClassInsnNode(NEW, "net/minecraft/network/play/server/SPacketBlockChange"));
		list.add(NPInsnNode.of(DUP));
		list.add(NPInsnNode.of(ALOAD_0));
		list.add(new FieldInsnNode(GETFIELD, "net/minecraft/server/management/PlayerInteractionManager", "field_73092_a", new Type("net/minecraft/world/World")));
		list.add(NPInsnNode.of(ALOAD_1));
		list.add(new InvokeInsnNode(INVOKESPECIAL, "net/minecraft/network/play/server/SPacketBlockChange", "<init>", "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"));
		list.add(new InvokeInsnNode(INVOKEVIRTUAL, "net/minecraft/network/NetHandlerPlayServer", "func_147359_a", "(Lnet/minecraft/network/Packet;)V"));
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
				ref.setClazz(data.cp.getClazz("ilib/asm/util/MCHooksClient"));
			}
		}
	}

	private static void replaceWithEmpty(ConstantData data, String name) {
		replaceWithEmpty(data, name, false);
	}
	private static CodeWriter replaceWithEmpty(ConstantData data, String name, boolean mod) {
		int i = data.getMethod(name);

		MethodNode ms = data.methods.get(i);

		Method mn = new Method(ms.accessFlag(), data, ms.name(), ms.rawDesc());
		data.methods.set(i, Helpers.cast(mn));

		Object code = ms.attrByName("Code");
		if (code != null) {
			if (mod) {
				AttrCodeWriter attr = new AttrCodeWriter(data.cp, ms);
				ms.attributes().putByName(attr);
				return attr.cw;
			}
			TransformUtil.trimCode(data, mn);
		}
		return Helpers.nonnull();
	}
}