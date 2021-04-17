package ilib.capabilities;

import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class TmpData implements ICapabilityProvider {
	public Object tmp;

	public Class<?> storedClass() {
		return tmp == null ? null : tmp.getClass();
	}

	public Object get() {
		return tmp;
	}

	public void set(Object tmp) {
		this.tmp = tmp;
	}

	public boolean hasCapability(@Nonnull Capability<?> cap, EnumFacing facing) {
		return cap == Capabilities.TEMP_STORAGE;
	}

	@SuppressWarnings("unchecked")
	public <T> T getCapability(@Nonnull Capability<T> cap, EnumFacing facing) {
		if (cap == Capabilities.TEMP_STORAGE) {
			return (T) this;
		}
		return null;
	}
}
