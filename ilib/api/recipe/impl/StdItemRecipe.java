package ilib.api.recipe.impl;

import ilib.api.recipe.IRecipe;
import roj.collect.MyBitSet;
import roj.util.Helpers;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StdItemRecipe extends AbstractItemRecipe implements IRecipe {
	public List<ItemStack> input = new ArrayList<>();

	public StdItemRecipe(String name, int mePerTick, int tickCost, boolean shaped) {
		super(name, mePerTick, tickCost, shaped);
	}

	public StdItemRecipe(String name, int me, int tick, boolean shaped, List<ItemStack> input, List<ItemStack> output) {
		super(name, me, tick, shaped, output);
		this.input = input;
	}

	/**
	 * @param stack null 强制为空 count=0 不消耗
	 */
	public <T extends StdItemRecipe> T addInput(ItemStack stack) {
		if (stack == null) {
			if (!isShaped()) {
				throw new IllegalStateException(getName() + " - 无序合成不支持留空");
			}
			this.input.add(null);
			return Helpers.cast(this);
		}
		if (stack.isEmpty()) {
			stack = stack.copy();
			stack.setCount(1);
			if (stack.isEmpty()) throw new NullPointerException(getName() + " - Non-consume stack is really empty");
			if (keepInputIds == null) {
				keepInputIds = new MyBitSet(this.input.size() + 1);
			}
			keepInputIds.add(this.input.size());
		}
		this.input.add(stack);
		return Helpers.cast(this);
	}

	@Override
	public List<ItemStack> getInput() {
		return this.input;
	}
}
