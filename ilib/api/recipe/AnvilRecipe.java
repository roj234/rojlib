package ilib.api.recipe;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class AnvilRecipe {
	private final ItemStack input1;
	private final ItemStack input2;
	private final int expCost;
	private final ItemStack output;

	public static final Map<Item, List<AnvilRecipe>> REGISTRY = new MyHashMap<>();

	public AnvilRecipe(int exp, ItemStack input1, ItemStack input2, ItemStack output) {
		this.expCost = exp;
		this.input1 = input1;
		this.input2 = input2;
		this.output = output;
		REGISTRY.computeIfAbsent(input1.getItem(), (k) -> new SimpleList<>()).add(this);
	}

	public int getExpCost() {
		return expCost;
	}

	public ItemStack getInput1() {
		return this.input1;
	}

	public ItemStack getInput2() {
		return this.input2;
	}

	public ItemStack getOutput() {
		return this.output;
	}
}