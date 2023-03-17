package ilib.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/**
 * @author Roj233
 * @since 2022/4/16 13:16
 */
public class LazyLoader {
	private TileEntity owner;
	private NBTTagCompound tag;

	public LazyLoader(TileEntity owner) {
		this.owner = owner;
	}

	public boolean readFromNBT(NBTTagCompound tag) {
		if (this.tag == null && owner != null) {
			this.tag = tag;
			if (tag.hasKey("x", 99)) owner.setPos(new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
			return false;
		} else {
			return true;
		}
	}

	public void onLoad() {
		if (tag != null) {
			owner.readFromNBT(tag);
			tag = null;
		}
		owner = null;
	}
}
