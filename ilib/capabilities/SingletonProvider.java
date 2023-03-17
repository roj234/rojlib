package ilib.capabilities;

import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/1/30 21:10
 */
public class SingletonProvider implements ICapabilityProvider {
	final Object inst;
	final Capability<?> cap;
	final EnumFacing face;

	public <T> SingletonProvider(T inst, Capability<T> cap) {
		this.inst = inst;
		this.cap = cap;
		this.face = null;
	}

	public <T> SingletonProvider(T inst, Capability<T> cap, EnumFacing face) {
		this.inst = inst;
		this.cap = cap;
		this.face = face;
	}

	@Override
	public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing enumFacing) {
		return capability == cap && enumFacing == face;
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T1> T1 getCapability(@Nonnull Capability<T1> capability, @Nullable EnumFacing enumFacing) {
		return capability == cap && enumFacing == face ? (T1) inst : null;
	}
}
