package ilib.api;

import ilib.tile.OwnerManager;

import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface Ownable {
	void setOwnType(int type);

	int getOwnType();

	void setOwner(EntityPlayer player);

	@Nullable
	OwnerManager getOwnerManager();
}