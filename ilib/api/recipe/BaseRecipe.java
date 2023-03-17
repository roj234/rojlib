package ilib.api.recipe;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class BaseRecipe implements IRecipe {
	public final String name;
	public final int mePerTick;
	public final int tickCost;
	private final boolean shaped;

	public BaseRecipe(String name, int mePerTick, int tickCost, boolean shaped) {
		this.name = name;
		this.mePerTick = mePerTick;
		this.tickCost = tickCost;
		this.shaped = shaped;
	}

	public final int getTimeCost() {
		return tickCost;
	}

	public final int getPowerCost() {
		return mePerTick;
	}

	@Override
	public boolean isShaped() {
		return shaped;
	}

	@Nonnull
	public final String getName() {
		return name;
	}
}
