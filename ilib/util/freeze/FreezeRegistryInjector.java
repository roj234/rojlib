package ilib.util.freeze;

import ilib.Config;
import ilib.ImpLib;
import ilib.util.ForgeUtil;
import ilib.util.Registries;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry.EntityRegistration;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry.MissingFactory;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Roj233
 * @since 2021/8/26 20:03
 */
public class FreezeRegistryInjector<T extends IForgeRegistryEntry<T>> implements MissingFactory<T>  {
	static int uniq;

	/**
	 * @see ilib.asm.nx.FastTileConst#create(World, NBTTagCompound)
	 *
	 */
	public static void inject() throws ReflectiveOperationException {
		Field acc = ForgeRegistry.class.getDeclaredField("missing");

		if (Config.freezeUnknownEntries.contains("item")) {
			acc.set(Registries.item(), new FreezeRegistryInjector<>(0));
		}

		if (Config.freezeUnknownEntries.contains("block")) {
			acc.set(Registries.item(), new FreezeRegistryInjector<>(1));
		}

		if (Config.freezeUnknownEntries.contains("entity")) {
			acc.set(Registries.item(), new FreezeRegistryInjector<>(2));

			ModContainer mc = ForgeUtil.findModById(ImpLib.MODID);
			EntityRegistration entry = EntityRegistry.instance()
				.new EntityRegistration(mc, new ResourceLocation("armor_stand"), FreezedEntity.class, "freezed", 12580, 64, 999999, false, null);

			// 对于只用一次的反射调用，就没必要用DirectAccessor了，开销反而更高, 占用了classid和metaspace
			Method m = EntityRegistry.class.getDeclaredMethod("insert", Class.class, EntityRegistration.class);
			m.setAccessible(true);
			m.invoke(EntityRegistry.instance(), FreezedTileEntity.class, entry);
		}

		if (Config.freezeUnknownEntries.contains("tile")) {
			TileEntity.register("ilib:freezed", FreezedTileEntity.class);
		}
	}

	final byte type;

	public FreezeRegistryInjector(int i) {
		this.type = (byte) i;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T createMissing(ResourceLocation reg, boolean b) {
		switch (type) {
			default:
			case 0:
				return (T) new FreezedItem().setRegistryName(reg);
			case 1:
				new Throwable().printStackTrace();
				return (T) new FreezedBlock().setRegistryName(reg);
			case 2:
				return (T) new EntityEntry(FreezedEntity.class, "freezed") {
					@Override
					public int hashCode() {
						return getRegistryName() == null ? 0 : getRegistryName().hashCode();
					}

					@Override
					public boolean equals(Object obj) {
						return obj instanceof EntityEntry && ((EntityEntry) obj).getRegistryName().equals(getRegistryName());
					}
				}.setRegistryName(reg);
		}
	}
}
