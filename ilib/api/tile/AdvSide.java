package ilib.api.tile;

import ilib.util.EnumIO;

import net.minecraft.util.EnumFacing;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Deprecated
public interface AdvSide {
	EnumIO getSideMode(int type, EnumFacing face);
}