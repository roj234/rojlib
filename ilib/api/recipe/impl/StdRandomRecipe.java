package ilib.api.recipe.impl;

import ilib.api.RandomEntry;
import ilib.api.recipe.IRandomRecipe;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StdRandomRecipe extends StdItemRecipe implements IRandomRecipe<StdRandomRecipe> {
	List<RandomEntry> randoms = new ArrayList<>();

	public StdRandomRecipe(String name, int me, int tick, boolean shaped) {
		super(name, me, tick, shaped);
	}

	public StdRandomRecipe(String name, int me, int tick, boolean shaped, List<ItemStack> input, List<ItemStack> output) {
		super(name, me, tick, shaped);
		this.input = input;
	}

	@Override
	public int getCount(int id, ItemStack stack) {
		if (id >= randoms.size()) {
			throw new IllegalArgumentException("[MI随机-" + getName() + "]随机列表大小不对! 需求: " + id + ", 实际: " + randoms.size());
		}
		RandomEntry entry = randoms.get(id);
		if (entry == null) return input.get(id).getCount();
		return entry.get();
	}

	@Override
	public int getMin(int id) {
		if (id >= randoms.size()) {
			throw new IllegalArgumentException("[MI随机-" + getName() + "]随机列表大小不对! 需求: " + id + ", 实际: " + randoms.size());
		}
		RandomEntry entry = randoms.get(id);
		if (entry == null) return input.get(id).getCount();
		return entry.min;
	}

	public StdRandomRecipe addNormal() {
		randoms.add(null);
		return this;
	}

	public StdRandomRecipe addRandom(int min, int max, double... factor) {
		randoms.add(new RandomEntry(min, max, factor));
		return this;
	}

	@Override
	public boolean isRandomized() {
		return randoms != null;
	}
}