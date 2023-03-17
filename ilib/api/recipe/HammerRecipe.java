package ilib.api.recipe;

import roj.collect.MyHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class HammerRecipe {
	private final IBlockState input;
	private final ItemStack output;
	private final int level;

	public static final Map<IBlockState, HammerRecipe> REGISTRY = new MyHashMap<>();

	static {
		create(Blocks.COBBLESTONE, Blocks.GRAVEL, 0);
		create(Blocks.GRAVEL, Blocks.SAND, 0);
	}

	public static void create(IBlockState in, Block out, int level) {
		new HammerRecipe(in, new ItemStack(out), level);
	}

	public static void create(Block in, Block out, int level) {
		new HammerRecipe(in.getDefaultState(), new ItemStack(out), level);
	}

	public HammerRecipe(IBlockState input, ItemStack output, int level) {
		this.input = input;
		this.output = output;
		this.level = level;
		REGISTRY.put(input, this);
	}

	public IBlockState getInput() {
		return this.input;
	}

	public int getLevel() {
		return this.level;
	}

	public ItemStack getOutput() {
		return this.output;
	}
}