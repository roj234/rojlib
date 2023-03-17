package ilib.api.tile;

import ilib.api.registry.Indexable;
import org.apache.commons.lang3.NotImplementedException;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * @author Roj234
 * @since 2021/6/2 23:45
 */
public interface ToolTarget {
	int NO_OP = -1, TOGGLE = 1, TURN = 2, DESTROY = 3, SPECIAL = 4;

	int ID_WRENCH = 0;

	class Type extends Indexable.Impl {
		public static final Type WRENCH = new Type("wrench", 100);
		public static final Type CUTTER = new Type("cutter", 300);

		public final int MAX_DAMAGE;

		private static int index;

		public Type(String name, int max_damage) {
			super(name, index++);
			this.MAX_DAMAGE = max_damage;
		}
	}


	int canUse(int tool_id, boolean isSneaking);

	// new: toggleByTool(int type, Object param);
	default void destroyByTool(int tool_id) {
		throw new NotImplementedException("Not implemented yet!");
	}

	default void toggleByTool(int tool_id, EnumFacing face) {
		throw new NotImplementedException("Not implemented yet!");
	}

	NBTTagCompound storeDestroyData();
}