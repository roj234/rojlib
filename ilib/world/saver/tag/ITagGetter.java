package ilib.world.saver.tag;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

/**
 * @author solo6975
 * @since 2022/3/31 21:29
 */
public interface ITagGetter {
	boolean isBusy();

	AsyncPacket getTag(TileEntity tr);

	AsyncPacket getTag(Entity e);

	boolean supportTile();

	boolean supportEntity();
}
