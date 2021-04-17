package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.SimpleList;
import roj.util.Helpers;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.common.FMLLog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/6 0006 21:21
 */
//使用preAT
@Nixim(value = "/", copyItf = true)
class FastCaps0 extends CapabilityDispatcher implements FastCaps1 {
	@Copy(unique = true)
	private NBTBase[] lazyData;

	FastCaps0() { super(null); }

	@Inject(value = "<init>", at = Inject.At.REPLACE)
	public void _initLast(Map<ResourceLocation, ICapabilityProvider> list, @Nullable ICapabilityProvider parent) {
		$$$CONSTRUCTOR();

		List<ICapabilityProvider> capList = new SimpleList<>(list.values());
		if (parent != null) capList.add(parent);

		capList.sort((o1, o2) -> {
			int v1 = o1 instanceof INBTSerializable ? 0 : 1;
			int v2 = o2 instanceof INBTSerializable ? 0 : 1;
			return Integer.compare(v1, v2);
		});

		caps = capList.toArray(new ICapabilityProvider[capList.size()]);

		int writables = 0;
		for (; writables < capList.size(); writables++) {
			if (!(capList.get(writables) instanceof INBTSerializable)) break;
		}

		names = new String[writables];
		if (trackable()) {
			lazyData = new NBTBase[writables];
		} else {
			writers = Helpers.cast(new INBTSerializable<?>[writables]);
			System.arraycopy(caps, 0, writers, 0, writables);
		}

		for (Map.Entry<ResourceLocation, ICapabilityProvider> entry : list.entrySet()) {
			for (int i = 0; i < writables; i++) {
				if (entry.getValue() == caps[i]) {
					names[i] = entry.getKey().toString();
					break;
				}
			}
		}

		if (parent instanceof INBTSerializable) {
			for (int i = 0; i < writables; i++) {
				if (parent == caps[i]) {
					names[i] = "Parent";
					break;
				}
			}
		}
	}

	private void $$$CONSTRUCTOR() {}

	@Copy(unique = true)
	private static int report;
	@Copy(unique = true)
	private boolean trackable() {
		Class<?> a = getClass(), b = CapabilityDispatcher.class;
		if (a == b) return true;
		try {
			boolean ok = a.getMethod("serializeNBT").equals(b.getMethod("serializeNBT")) &&
				a.getMethod("deserializeNBT", NBTTagCompound.class).equals(b.getMethod("deserializeNBT", NBTTagCompound.class)) &&
				a.getMethod("getCapability", Capability.class, EnumFacing.class).equals(b.getMethod("getCapability", Capability.class, EnumFacing.class));
			if (!ok && report++ < 10) {
				FMLLog.bigWarning("草真有人这么干啊.jpg 报告ImpLib作者啊谢谢你了");
			}
			return ok;
		} catch (Exception e) {
			return false;
		}
	}

	@Copy
	public NBTBase[] getLazyData() {
		if (lazyData != null) {
			for (int i = 0; i < lazyData.length; i++) {
				if (lazyData[i] == null) {
					lazyData[i] = ((INBTSerializable<?>)caps[i]).serializeNBT();
				}
			}
		}
		return lazyData;
	}

	@Inject("/")
	public boolean areCompatible(CapabilityDispatcher o) {
		if (o == this) return true;
		if (o == null) return names.length == 0;
		if (names.length != o.names.length) return false;
		if (names.length == 0) return true;

		NBTBase[] selfNbt = getLazyData();
		// noinspection all
		NBTBase[] otherNbt = ((FastCaps1)(Object)o).getLazyData();
		if (selfNbt != null) {
			if (otherNbt != null) return fullEq(selfNbt, otherNbt);
			else return halfEq(selfNbt, names, o.serializeNBT());
		} else if (otherNbt != null) {
			return halfEq(otherNbt, o.names, serializeNBT());
		} else {
			return serializeNBT().equals(o.serializeNBT());
		}
	}

	@Copy(unique = true)
	private static boolean fullEq(NBTBase[] a, NBTBase[] b) {
		int len = a.length;

		for (int i=0; i<len; i++) {
			NBTBase o1 = a[i], o2 = b[i];
			if (!(o1==null ? o2==null : o1.equals(o2))) return false;
		}

		return true;
	}
	@Copy(unique = true)
	private static boolean halfEq(NBTBase[] a, String[] names, NBTTagCompound tag) {
		int len = a.length;

		for (int i = 0; i<len; i++) {
			NBTBase o1 = a[i], o2 = tag.getTag(names[i]);
			if (!(o1==null ? o2==null : o1.equals(o2))) return false;
		}

		return true;
	}

	@Inject("/")
	public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
		ICapabilityProvider[] caps = this.caps;
		for (int i = 0; i < caps.length; i++) {
			ICapabilityProvider cap = caps[i];

			T ret = cap.getCapability(capability, facing);
			if (ret != null) {
				if (lazyData != null && cap instanceof INBTSerializable) lazyData[i] = null;
				return ret;
			}
		}

		return null;
	}

	@Inject("/")
	public NBTTagCompound serializeNBT() {
		NBTTagCompound nbt = new NBTTagCompound();

		NBTBase[] lazy = lazyData;
		for(int i = 0; i < names.length; i++) {
			NBTBase tag = ((INBTSerializable<?>)caps[i]).serializeNBT();
			nbt.setTag(names[i], tag);

			if (lazy != null) lazy[i] = tag;
		}

		return nbt;
	}

	@Inject("/")
	public void deserializeNBT(NBTTagCompound nbt) {
		NBTBase[] lazy = lazyData;
		for(int i = 0; i < names.length; i++) {
			NBTBase tag = nbt.getTag(names[i]);
			if (tag != null) {
				((INBTSerializable<?>)caps[i]).deserializeNBT(Helpers.cast(tag));
				if (lazy != null) lazy[i] = tag;
			}
		}
	}
}
