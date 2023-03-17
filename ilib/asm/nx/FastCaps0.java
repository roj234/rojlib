package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/6 0006 21:21
 */
//使用preAT
@Nixim(value = "/", copyItf = true)
class FastCaps0 extends CapabilityDispatcher implements FastCaps2 {
	FastCaps0() {
		super(null);
	}

	@Inject(value = "<init>", at = Inject.At.TAIL)
	private void _initLast(Map<ResourceLocation, ICapabilityProvider> list, @Nullable ICapabilityProvider parent) {
		Arrays.sort(names);
	}

	@Copy(unique = true)
	private NBTBase[] lazyData;
	@Copy(unique = true)
	private boolean trackable;
	@Copy
	public void setTrackable() {
		trackable = true;
		if (lazyData == null) lazyData = new NBTBase[names.length];
	}
	@Copy
	public boolean isTrackable() {
		return trackable;
	}
	@Copy
	public NBTBase[] getLazyData() {
		for (int i = 0; i < lazyData.length; i++) {
			if (lazyData[i] == null) {
				lazyData[i] = writers[i].serializeNBT();
			}
		}
		return lazyData;
	}

	@Inject("/")
	public boolean areCompatible(CapabilityDispatcher o) {
		if (o == null) {
			return writers.length == 0;
		} else if (writers.length == 0) {
			return o.writers.length == 0;
		}

		FastCaps2 self = _isTrackable(this);
		FastCaps2 other = _isTrackable(o);
		if (self.isTrackable()) {
			if (other.isTrackable()) {
				return fullEq(self.getLazyData(), other.getLazyData());
			} else {
				return halfEq(self.getLazyData(), names, o.serializeNBT());
			}
		} else if (other.isTrackable()) {
			return halfEq(other.getLazyData(), o.names, serializeNBT());
		} else {
			return serializeNBT().equals(o.serializeNBT());
		}
	}

	@Copy(unique = true)
	private static FastCaps2 _isTrackable(CapabilityDispatcher caps0) {
		return ((FastCaps2)(Object)caps0);
	}
	@Copy(unique = true)
	private static boolean fullEq(NBTBase[] a, NBTBase[] b) {
		int len = a.length;
		if (b.length != len) return false;

		for (int i=0; i<len; i++) {
			NBTBase o1 = a[i], o2 = b[i];
			if (!(o1==null ? o2==null : o1.equals(o2))) return false;
		}

		return true;
	}
	@Copy(unique = true)
	private static boolean halfEq(NBTBase[] a, String[] names, NBTTagCompound tag) {
		int len = a.length;
		if (tag.getSize() != len) return false;

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
				if (lazyData != null) lazyData[i] = null;
				return ret;
			}
		}

		return null;
	}

	@Inject("/")
	public NBTTagCompound serializeNBT() {
		NBTTagCompound nbt = new NBTTagCompound();

		NBTBase[] lazy = lazyData;
		INBTSerializable<NBTBase>[] w = writers;
		for(int i = 0; i < w.length; i++) {
			NBTBase tag = w[i].serializeNBT();
			nbt.setTag(names[i], tag);

			if (lazy != null) lazy[i] = tag;
		}

		return nbt;
	}

	@Inject("/")
	public void deserializeNBT(NBTTagCompound nbt) {
		NBTBase[] lazy = lazyData;
		INBTSerializable<NBTBase>[] w = writers;
		for(int i = 0; i < w.length; i++) {
			NBTBase tag = nbt.getTag(names[i]);
			if (tag != null) {
				w[i].deserializeNBT(tag);
				if (lazy != null) lazy[i] = tag;
			}
		}
	}
}
