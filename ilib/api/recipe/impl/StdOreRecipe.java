package ilib.api.recipe.impl;

import ilib.ImpLib;
import ilib.api.recipe.IRecipe;
import ilib.api.recipe.MultiInputRecipe;
import ilib.fluid.handler.IFluidProvider;
import ilib.util.InventoryUtil;
import roj.collect.MyBitSet;
import roj.util.Helpers;
import roj.util.Idx;

import net.minecraft.item.ItemStack;

import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StdOreRecipe extends AbstractItemRecipe implements MultiInputRecipe {
	final List<ItemStack[]> input;

	public StdOreRecipe(String name, int me, int tick) {
		this(name, me, tick, false);
	}

	public StdOreRecipe(String name, int me, int tick, boolean shaped) {
		super(name, me, tick, shaped);
		this.input = new ArrayList<>();
	}

	public StdOreRecipe(String name, int me, int tick, boolean shaped, List<ItemStack[]> input, List<ItemStack> output) {
		super(name, me, tick, shaped, output);
		this.input = input;
	}

	/**
	 * Notice: Different count is supported
	 */
	public StdOreRecipe addMultiInput(ItemStack[] stacks) {
		this.input.add(stacks);
		return this;
	}

	public StdOreRecipe addOreInput(String ore, int count) {
		if (count < 0) throw new UnsupportedOperationException("Ore non-consume was not supported.");
		List<ItemStack> ores = OreDictionary.getOres(ore, false);
		if (ores.isEmpty()) {
			throw new IllegalArgumentException("The ore specified (" + ore + ") doesn't has item.");
		}

		ItemStack[] targetStacks = new ItemStack[ores.size()];
		int i = 0;
		for (ItemStack stack : ores) {
			if (stack.getCount() != count) {
				stack = stack.copy();
				stack.setCount(count);
			}
			targetStacks[i++] = stack;
		}
		this.input.add(targetStacks);
		return this;
	}

	public <T extends StdOreRecipe> T addInput(ItemStack stack) {
		if (stack.isEmpty()) {
			stack = stack.copy();
			stack.setCount(1);
			if (stack.isEmpty()) throw new NullPointerException("Non-consume stack is really empty");
			if (keepInputIds == null) {
				keepInputIds = new MyBitSet(this.input.size() + 1);
			}
			keepInputIds.add(this.input.size());
		}
		this.input.add(new ItemStack[] {stack});

		return Helpers.cast(this);
	}

	public boolean matches(IFluidProvider fp, @Nonnull List<ItemStack> list) {
		return isShaped() ? matchesOreShaped(this, list) : matchesOreShapeless(this, list);
	}

	/**
	 * 矿物词典无序合成匹配函数
	 *
	 * @param recipe 合成
	 * @param list 输入
	 *
	 * @return 物品是否匹配
	 */
	public static boolean matchesOreShapeless(@Nonnull MultiInputRecipe recipe, @Nonnull List<ItemStack> list) {
		List<ItemStack[]> input0 = recipe.getMultiInputs();
		int inputLen = input0.size();
		if (list.size() < inputLen) return false;

		Idx recipeSlot = new Idx(inputLen);

		outer:
		for (ItemStack stack : list) {
			if (stack.isEmpty()) {
				continue;
			}

			if (recipeSlot.isFull()) { // 只需求一种物品且输入槽全是这种物品
				return inputLen == 1;
			}

			PrimitiveIterator.OfInt itr = recipeSlot.remains();
			while (itr.hasNext()) {
				int j = itr.nextInt();
				ItemStack[] crafts = input0.get(j);
				for (ItemStack crafting : crafts) {
					if (InventoryUtil.areItemStacksEqual(crafting, stack) && stack.getCount() >= crafting.getCount()) {
						//PlayerUtil.broadcastAll("Succeed at " + list.indexOf(stack) + ", " + j);
						recipeSlot.add(j);
						continue outer;
					}
				}
			}
			//            PlayerUtil.broadcastAll(recipe.getValue() + " Failed at " + list.indexOf(stack) + " cause not ore matches: " + stack);
			//            itr = recipeSlot.remains();
			//            while (itr.hasNext()) {
			//                int j = itr.nextInt();
			//                ItemStack[] crafts = input0.get(j);
			//                PlayerUtil.broadcastAll(j + ":" + Arrays.toString(crafts));
			//            }
			return false;
		}
		return recipeSlot.isFull();
	}

	/**
	 * 矿物词典有序合成匹配函数
	 *
	 * @param recipe 合成
	 * @param list 输入
	 *
	 * @return 物品是否匹配
	 */
	public static boolean matchesOreShaped(@Nonnull MultiInputRecipe recipe, @Nonnull List<ItemStack> list) {
		List<ItemStack[]> input0 = recipe.getMultiInputs();
		int inputLen = input0.size();
		if (list.size() != input0.size()) {
			ImpLib.logger().error("[ShapedOreRecipe-" + recipe.getName() + "]物品列表大小不对! 需求: " + list.size() + ", 实际: " + input0.size());
			return false;
		}

		outer:
		for (int i = 0, k = list.size(); i < k; i++) {
			ItemStack stack = list.get(i);
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack[] crafts = input0.get(i);
			for (ItemStack crafting : crafts) {
				if (InventoryUtil.areItemStacksEqual(crafting, stack) && stack.getCount() >= crafting.getCount()) {
					continue outer;
				}
			}
			return false;
		}
		return true;
	}

	@Nonnull
	@Override
	public List<ItemStack> operateInput(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> input) {
		return isShaped() ? operateMultiShaped(this, input) : operateMultiShapeless(this, input);
	}

	/**
	 * 矿物词典有序合成处理函数
	 *
	 * @param recipe 合成
	 * @param input 输入
	 *
	 * @return 合成后的物品列表
	 */
	public static List<ItemStack> operateMultiShaped(MultiInputRecipe recipe, List<ItemStack> input) {
		int inputLen = input.size();
		List<ItemStack[]> list = recipe.getMultiInputs();
		if (inputLen != list.size()) throw new IllegalStateException("NFSIZEError");

		outer:
		for (int i = 0; i < inputLen; i++) {
			ItemStack selfStack = input.get(i);
			if (selfStack.isEmpty()) {
				continue;
			}
			ItemStack[] stacks = list.get(i);
			for (ItemStack recipeStack : stacks) {
				if (IRecipe.stackEquals(selfStack, recipeStack)) {
					if (recipe.willConsume(i)) IRecipe.decrStackSize(input, i, recipeStack.getCount());
					continue outer;
				}
			}
			throw new IllegalStateException("[MI矿辞-" + recipe.getName() + "] slotId " + i + ":  没有找到输入");
		}
		return input;
	}

	/**
	 * 矿物词典无序合成处理函数
	 *
	 * @param recipe 合成
	 * @param input 输入
	 *
	 * @return 合成后的物品列表
	 */
	public static List<ItemStack> operateMultiShapeless(@Nonnull MultiInputRecipe recipe, @Nonnull List<ItemStack> input) {
		List<ItemStack[]> list = recipe.getMultiInputs();
		Idx idx = new Idx(list.size());

		int inputLen = input.size();
		outer:
		for (int i = 0; i < inputLen; i++) {
			ItemStack selfStack = input.get(i);
			if (selfStack.isEmpty()) {
				continue;
			}

			PrimitiveIterator.OfInt itr = idx.remains();
			while (itr.hasNext()) {
				int j = itr.nextInt();
				ItemStack[] stacks = list.get(j);
				for (ItemStack recipeStack : stacks) {
					if (IRecipe.stackEquals(selfStack, recipeStack)) {
						if (recipe.willConsume(j)) IRecipe.decrStackSize(input, i, recipeStack.getCount());
						idx.add(j);
						continue outer;
					}
				}
			}
			throw new IllegalStateException("[MI矿辞-" + recipe.getName() + "] slotId " + i + ":  没有找到输入");
		}
		if (!idx.isFull()) {
			throw new IllegalStateException("[MI矿辞-" + recipe.getName() + "] 没有`全部`找到输入");
		}
		return input;
	}

	public final List<ItemStack[]> getMultiInputs() {
		return this.input;
	}
}