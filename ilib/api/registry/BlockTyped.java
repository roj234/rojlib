package ilib.api.registry;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/1 12:41
 */
public interface BlockTyped {
	default String getTool() {
		return "pickaxe";
	}

	int harvestLevel(int offset);

	float getResistance(int offset);

	float getHardness(int offset);
}