package ilib.math;

import net.minecraft.util.math.BlockPos;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface PositionProvider {
	Section getSection();

	int getWorld();

	default boolean contains(BlockPos pos) {
		return true;
	}

	default void handle(FastPath<? extends PositionProvider> fastPath) {
	}
}
