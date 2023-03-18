package ilib.misc;

import ilib.client.KeyRegister;
import roj.asm.tree.ConstantData;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.collect.FilterList;
import roj.collect.IntSet;
import roj.collect.ToIntMap;
import roj.concurrent.OperationDone;
import roj.reflect.DirectAccessor;
import roj.reflect.FastInit;
import roj.reflect.TraceUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * @author Roj234
 * @since 2020/8/23 0:55
 */
public class MCHooks {
	public static final ThreadLocal<MutAABB> box2 = ThreadLocal.withInitial(MutAABB::new);
	public static final MutAABB box1 = new MutAABB();

	// 自定义TPS
	public static long MSpT = 50L;
	private static long lastSaveTime;

	public static boolean shouldSaveChunk(boolean all) {
		if (MSpT < 50) {
			long time = System.currentTimeMillis();
			if (time - lastSaveTime > 60000) {
				lastSaveTime = time;
			} else {
				return false;
			}
		}
		return true;
	}

	public static Thread traceNew;

	// 空方法
	public static void empty() {}

	// region FastTileConst/FastEntityConst: 批量生成Entity/TileEntity

	public static Object batchGenerate(RandomAccessFile raf, boolean entity, ToIntMap<String> byId) throws IOException {
		if (raf.length() == 0) {
			raf.writeInt(0);
			return null;
		}

		int len = raf.readInt();
		if (len == 0) return null;

		ConstantData cz = new ConstantData();
		DirectAccessor.makeHeader(entity ? "ilib/asm/BatchEntityCreator" : "ilib/asm/BatchTileCreator", "ilib/asm/util/ICreator", cz);
		cz.version = 50 << 16;
		FastInit.prepare(cz);
		cz.cloneable();
		if (entity) cz.addInterface("java/util/function/Function");

		cz.newField(0, "id", Type.std(Type.INT));

		CodeWriter cw = cz.newMethod(PUBLIC, "setId", "(I)V");
		cw.visitSize(2,2);

		cw.one(ALOAD_0);
		cw.one(ILOAD_1);
		cw.field(PUTFIELD, cz.name, "id", Type.std(Type.INT));
		cw.one(RETURN);

		cw = cz.newMethod(PUBLIC, entity ? "apply" : "get",
			entity ? "(Ljava/lang/Object;)Ljava/lang/Object;" : "()Lnet/minecraft/tileentity/TileEntity;");
		cw.visitSize(entity ? 3 : 2,  entity ? 2 : 1);

		if (entity) {
			cw.one(ALOAD_1);
			cw.clazz(CHECKCAST, "net/minecraft/world/World");
			cw.one(ASTORE_1);
		}

		cw.one(ALOAD_0);
		cw.field(GETFIELD, cz.name, "id", Type.std(Type.INT));

		SwitchSegment _switch = CodeWriter.newSwitch(TABLESWITCH);
		cw.switches(_switch);
		_switch.def = cw.label();
		cw.one(ACONST_NULL);
		cw.one(ARETURN);

		for (int i = 0; i < len; i++) {
			byId.putInt(raf.readUTF(), i);
			String clz = raf.readUTF();

			Label label = cw.label();
			_switch.targets.add(new SwitchEntry(i, label));

			cw.clazz(NEW, clz);
			cw.one(DUP);
			if (entity) {
				cw.one(ALOAD_1);
				cw.invoke(INVOKESPECIAL, clz, "<init>", "(Lnet/minecraft/world/World;)V");
			} else {
				cw.invoke(INVOKESPECIAL, clz, "<init>", "()V");
			}
			cw.one(ARETURN);
		}

		return FastInit.make(cz);
	}

	public static void batchAdd(RandomAccessFile raf, String id, Class<?> clazz) throws IOException {
		long pos = raf.getFilePointer();

		raf.seek(0);
		int count = raf.readInt();

		raf.seek(0);
		raf.writeInt(count+1);

		raf.seek(pos);
		raf.writeUTF(id);
		raf.writeUTF(clazz.getName().replace('.', '/'));
	}

	// endregion
	// region FastRecipe: 高效的合成map

	// 服务器上可能有多个打开的工作台
	public interface RecipeCache {
		IRecipe getRecipe();

		void setRecipe(IRecipe recipe);
	}

	// 被MoreItems使用
	public static boolean tryFindOD(List<Object> stacks, IntSet commonOreDict, IntSet tmp, ItemStack[] matches) {
		ii:
		if (matches.length < 2) {
			if (matches.length == 1) {
				ItemStack stack = matches[0];
				ResourceLocation key = stack.getItem().getRegistryName();
				if (stack.getItemDamage() == 32767) {
					// noinspection all
					stacks.add(key.toString());
				} else if (!stack.isEmpty()) {
					// noinspection all
					stacks.add(key.toString() + ':' + stack.getItemDamage());
				} else {
					stacks.add("minecraft:air");
				}
				return false;
			} else {
				System.out.println("Not know what is this... len=0 air?");
			}
		} else {
			for (ItemStack stack : matches) {
				if (commonOreDict.isEmpty()) {
					commonOreDict.addAll(OreDictionary.getOreIDs(stack));
				} else {
					tmp.clear();
					tmp.addAll(OreDictionary.getOreIDs(stack));
					commonOreDict.intersection(tmp);
				}
				if (commonOreDict.isEmpty()) break ii;
			}
			if (commonOreDict.size() == 1) {
				stacks.add(OreDictionary.getOreName(commonOreDict.iterator().nextInt()));
				return false;
			}
		}
		stacks.add(matches);
		commonOreDict.clear();
		return true;
	}

	// endregion
	// region 开启红石优化(NoSoManyBlockPos)
	public static final EnumFacing[] REDSTONE_UPDATE_ORDER = new EnumFacing[] {EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH};
	public static final EntityEquipmentSlot[] SLOT_VALUES = EntityEquipmentSlot.values();

	public static EnumFacing[] values() {
		return EnumFacing.VALUES;
	}

	public static EntityEquipmentSlot[] slots() {
		return SLOT_VALUES;
	}
	// endregion

	public static int getStackDepth() {
		int i = TraceUtil.INSTANCE.classDepth("java.lang.Thread");
		if (i > 0) return i;
		return TraceUtil.stackDepth(new Throwable());
	}

	private static final FilterList<Entity> list = new FilterList<>(((old, latest) -> {
		if (latest != null) {
			if (!latest.isDead && latest.preventEntitySpawning && (old == null || !latest.isRidingSameEntity(old))) {
				throw OperationDone.INSTANCE;
			}
		}
		return false;
	}));

	public static FilterList<Entity> getEntityAliveFilter(Entity entity) {
		list.found = entity;
		return list;
	}

	// region NoEnchantTax/NoAnvilTax: 禁用经验等级

	public static void playerAnvilClick(EntityPlayer player, int level) {
		player.addExperience(-get02LScores(level));
	}

	public static int get02LScores(int cost) {
		int i = 0;
		for (int j = 0; j < cost; j++) {
			i += xpCap(j);
		}
		return i;
	}

	// 0-30级所需经验
	public static final int ench30s = 37 + (27 - 15) * 5 + 37 + (28 - 15) * 5 + 37 + (29 - 15) * 5;

	public static int xpCap(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		} else {
			return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
		}
	}

	// endregion
	// region 独立的脱离按键
	@SideOnly(Side.CLIENT)
	public static String dismountMessage(String id, Object[] data) {
		data[0] = KeyRegister.keyDismount.getDisplayName();
		return I18n.format(id, data);
	}

	// endregion
	// region 高效射线追踪
	public static final Vec3d d = new Vec3d(0, 0, 0);
	public static final Vec3d e = new Vec3d(0, 0, 0);
	// endregion
}
