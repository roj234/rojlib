package ilib.util.freeze;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * 冻结的方块实体
 *
 * @author Roj233
 * @since 2021/8/26 19:36
 */
public class FreezedTileEntity extends TileEntity {
	public FreezedTileEntity() {
		this.isInitial = true;
		new Throwable("未授权的创建").printStackTrace();
	}

	public FreezedTileEntity(NBTTagCompound tag) {
		this.tag = tag;
	}

	public boolean isInitial;
	public NBTTagCompound tag;

	@Override
	public NBTTagCompound getUpdateTag() {
		return new NBTTagCompound();
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		isInitial = false;
		this.tag = tag;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		if (this.tag != null) {
			for (String key : this.tag.getKeySet()) {
				tag.setTag(key, this.tag.getTag(key));
			}
		}
		return tag;
	}
}
