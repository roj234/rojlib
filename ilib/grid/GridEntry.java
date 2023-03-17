package ilib.grid;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * @author Roj233
 * @since 2022/5/13 16:12
 */
public interface GridEntry {
	default String getEnergyUnit() {return "ME";}

	default GridEntry transform(String otherUnit) {return null;}

	BlockPos pos();

	default void enterGrid(Grid grid) {}

	default void exitGrid(Grid grid) {}

	default void gridUpdate() {}

	// 用电器/电线才会调用 null表示整体电阻(电线)
	default float getResistance(EnumFacing side) {
		return 0;
	}

	// region 发电机
	default boolean canProvidePower() {return false;}

	default Power providePower(EnumFacing side) {return null;}
	// endregion

	// region 用电器

	/**
	 * 具体到控制某面能否接收能量请覆盖getResistance
	 */
	default boolean canConsumePower() {
		return false;
	}

	default void consumePower(float voltage, double current) {}
	// endregion
}
