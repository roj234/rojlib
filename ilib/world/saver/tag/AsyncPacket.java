package ilib.world.saver.tag;

import net.minecraft.nbt.NBTTagCompound;

/**
 * @author solo6975
 * @since 2022/3/31 22:03
 */
public class AsyncPacket {
	private NBTTagCompound tag;

	public void setDone(NBTTagCompound tag) {
		this.tag = tag;
	}

	public boolean finished() {
		return tag != null;
	}

	public NBTTagCompound get() {
		return tag;
	}
}
