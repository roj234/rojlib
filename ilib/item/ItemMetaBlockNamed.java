package ilib.item;

import ilib.api.registry.BlockPropTyped;
import ilib.api.registry.Localized;
import ilib.api.registry.Propertied;
import ilib.util.ForgeUtil;
import ilib.util.MCTexts;
import roj.io.IOUtil;
import roj.text.CharList;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class ItemMetaBlockNamed<T extends Propertied<T> & Localized> extends ItemMetaBlock<T> {
	protected String i18n;

	public ItemMetaBlockNamed(Block block, BlockPropTyped<T> prop, String name, String propName) {
		super(block, prop, name, propName);
		this.i18n = setDefault(block, name);
	}

	public ItemMetaBlockNamed(Block block, String name) {
		super(block, name);
		this.i18n = setDefault(block, name);
	}

	static String setDefault(Block block, String name) {
		ResourceLocation key = block.getRegistryName();
		String v = key == null ? ForgeUtil.getCurrentModId() : key.getNamespace();
		return "tile." + v + '.' + name;
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		T t = getTypeByStack(stack);
		if (t == null) return MCTexts.format("invalid");

		String base = MCTexts.format(i18n);
		CharList tmp = IOUtil.getSharedCharBuf();
		if (base.contains("{}")) return tmp.append(base).replace("{}", t.getLocalizedName()).toString();
		return tmp.append(t.getLocalizedName()).append(base).toString();
	}

	public void setName(String param) {
		this.i18n = param;
	}
}