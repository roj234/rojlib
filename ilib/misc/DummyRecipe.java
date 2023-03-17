package ilib.misc;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * @author solo6975
 * @since 2022/4/6 20:41
 */
public class DummyRecipe implements IRecipe {
	public static final ItemStack missingNo;

	static {
		NBTTagCompound tag = new NBTTagCompound();
		NBTTagCompound display = new NBTTagCompound();
		display.setString("Name", "此合成已被删除或不存在");
		tag.setTag("display", display);
		ItemStack stack = new ItemStack(Blocks.BEDROCK, 1);
		stack.setTagCompound(tag);
		missingNo = stack;
	}

	private final ItemStack output;
	private ResourceLocation name;

	public DummyRecipe() {
		this.output = missingNo;
	}

	public DummyRecipe(ItemStack output) {
		this.output = output;
	}

	public Class<IRecipe> getRegistryType() {
		return IRecipe.class;
	}

	public static IRecipe from(IRecipe other) {
		return new DummyRecipe(other.getRecipeOutput()).setRegistryName(other.getRegistryName());
	}

	public IRecipe setRegistryName(ResourceLocation name) {
		this.name = name;
		return this;
	}

	public ResourceLocation getRegistryName() {
		return this.name;
	}

	@Override
	public boolean matches(@Nonnull InventoryCrafting inv, @Nonnull World worldIn) {
		return false;
	}

	@Nonnull
	@Override
	public ItemStack getCraftingResult(@Nonnull InventoryCrafting inv) {
		return output;
	}

	@Override
	public boolean canFit(int width, int height) {
		return false;
	}

	public boolean isDynamic() {
		return true;
	}

	@Nonnull
	@Override
	public ItemStack getRecipeOutput() {
		return output;
	}
}
