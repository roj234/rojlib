package ilib.client;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CreativeTabsMy extends CreativeTabs {
	private ItemStack iconStack = ItemStack.EMPTY;
	private boolean search;

	public CreativeTabsMy(String name) {
		super(name);
	}

	public CreativeTabsMy setBackground(String bg) {
		int i = bg.indexOf(":");
		this.setBackgroundImageName(i != -1 ? bg.substring(0, i) + ":textures/gui/" + bg.substring(i + 1) + ".png" : "ilib:textures/gui/" + bg + ".png");
		return this;
	}

	public CreativeTabsMy setIcon(ItemStack stack) {
		this.iconStack = stack;
		return this;
	}

	public CreativeTabsMy setSearchable() {
		this.search = true;
		return this;
	}

	@Override
	public boolean hasSearchBar() {
		return search;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public ResourceLocation getBackgroundImage() {
		return new ResourceLocation(getBackgroundImageName().equals("items.png") ? "textures/gui/container/creative_inventory/tab_items.png" : getBackgroundImageName());
	}

	@Nonnull
	@Override
	public ItemStack getIcon() {
		return iconStack;
	}

	@Override
	public ItemStack createIcon() {
		return iconStack;
	}
}