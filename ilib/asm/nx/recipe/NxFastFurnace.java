package ilib.asm.nx.recipe;

import ilib.collect.ItemStackMap;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.FMLLog;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.item.crafting.FurnaceRecipes")
abstract class NxFastFurnace {
	@Shadow
	private Map<ItemStack, ItemStack> smeltingList;
	@Shadow
	private Map<ItemStack, Float> experienceList;

	@Inject(value = "<init>", at = Inject.At.TAIL)
	public NxFastFurnace() {
		smeltingList = new ItemStackMap<>(smeltingList);
		experienceList = new ItemStackMap<>(experienceList);
	}

	@Inject
	public void addSmeltingRecipe(ItemStack input, ItemStack stack, float experience) {
		ItemStack out = this.getSmeltingResult(input);
		if (out != ItemStack.EMPTY) {
			FMLLog.log.info("冲突的熔炉合成: {} => {} 和 {}", input, stack, out);
		} else {
			this.smeltingList.put(input, stack);
			this.experienceList.put(stack, experience);
		}
	}

	/**
	 * HashMap的实现: comparableItemStack.equals(storedStack)
	 */
	@Inject
	public ItemStack getSmeltingResult(ItemStack stack) {
		return smeltingList.getOrDefault(stack, ItemStack.EMPTY);
	}

	@Inject
	public float getSmeltingExperience(ItemStack stack) {
		float ret = stack.getItem().getSmeltingExperience(stack);
		if (ret != -1) {
			return ret;
		} else {
			return experienceList.getOrDefault(stack, 0.0f);
		}
	}
}
