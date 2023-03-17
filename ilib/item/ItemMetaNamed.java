package ilib.item;

import ilib.api.registry.IRegistry;
import ilib.api.registry.Localized;
import ilib.util.MCTexts;
import roj.io.IOUtil;
import roj.text.CharList;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 基于{@link Localized}创建自定翻译的Meta物品
 */
public class ItemMetaNamed<T extends Localized> extends ItemMeta<T> {
	private String i18n;

	public ItemMetaNamed(String itemName, IRegistry<T> wrapper) {
		super(itemName, wrapper);
	}

	public ItemMetaNamed(String itemName, String textureLocation, IRegistry<T> wrapper) {
		super(itemName, textureLocation, wrapper);
	}

	@Nonnull
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