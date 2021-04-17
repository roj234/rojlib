package ilib.asm.util;

import net.minecraft.tileentity.TileEntity;

/**
 * @author Roj234
 * @since 2020/8/20 18:04
 */
public interface ICreator {
	default void setId(int id) {
		throw new UnsupportedOperationException();
	}

	default Object clone() {
		throw new UnsupportedOperationException();
	}

	TileEntity get();
}
