/**
 * This file is randoms part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: StandardRecipe.java
 */
package ilib.api.recipe.impl;

import ilib.api.RandomEntry;
import ilib.api.recipe.IRandomRecipe;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StdRandomOreRecipe extends StdOreRecipe implements IRandomRecipe<StdRandomOreRecipe> {
	public final List<RandomEntry> randoms = new ArrayList<>();

	public StdRandomOreRecipe(String name, int me, int tick) {
		super(name, me, tick);
	}

	public StdRandomOreRecipe(String name, int me, int tick, boolean shaped) {
		super(name, me, tick, shaped);
	}

	public StdRandomOreRecipe(String name, int me, int tick, boolean shaped, List<ItemStack[]> input, List<ItemStack> output) {
		super(name, me, tick, shaped, input, output);
	}

	@Override
	public int getCount(int id, ItemStack stack) {
		if (id >= randoms.size()) {
			throw new IllegalArgumentException("[MI矿物随机-" + getName() + "]随机列表大小不对! 需求: " + id + ", 实际: " + randoms.size());
		}
		RandomEntry entry = randoms.get(id);
		if (entry == null) return input.get(id)[0].getCount();
		return entry.get();
	}

	@Override
	public int getMin(int id) {
		if (id >= randoms.size()) {
			throw new IllegalArgumentException("[MI矿物随机-" + getName() + "]随机列表大小不对! 需求: " + id + ", 实际: " + randoms.size());
		}
		RandomEntry entry = randoms.get(id);
		if (entry == null) return input.get(id)[0].getCount();
		return entry.min;
	}

	public StdRandomOreRecipe addNormal() {
		randoms.add(null);
		return this;
	}

	public StdRandomOreRecipe addRandom(int min, int max, double... factor) {
		randoms.add(new RandomEntry(min, max, factor));
		return this;
	}

	@Override
	public final boolean isRandomized() {
		return true;
	}
}