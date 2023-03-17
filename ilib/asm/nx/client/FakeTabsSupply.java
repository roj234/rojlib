package ilib.asm.nx.client;

import ilib.api.client.FakeTab;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

/**
 * @author Roj234
 * @since 2020/9/12 22:46
 */
@Nixim("net.minecraft.creativetab.CreativeTabs")
abstract class FakeTabsSupply extends CreativeTabs {
	@Shadow("field_78034_o")
	String tabLabel;
	@Shadow("field_78043_p")
	String backgroundTexture;
	@Shadow("field_78042_q")
	boolean hasScrollbar;
	@Shadow("field_78041_r")
	boolean drawTitle;
	@Shadow("field_111230_s")
	EnumEnchantmentType[] enchantmentTypes;
	@Shadow("field_151245_t")
	ItemStack icon;
	@Shadow("field_78033_n")
	int index;

	FakeTabsSupply() {
		super(null);
	}

	void $$$CONSTRUCTOR() {}

	@Inject(value = "<init>", at = At.REPLACE)
	public void remapToInit(int index, String label) {
		$$$CONSTRUCTOR();

		this.tabLabel = label;

		this.backgroundTexture = "items.png";
		this.hasScrollbar = true;
		this.drawTitle = true;
		this.enchantmentTypes = new EnumEnchantmentType[0];

		this.icon = ItemStack.EMPTY;

		if (this instanceof FakeTab) return;

		this.index = index;

		if (index >= CREATIVE_TAB_ARRAY.length) {
			CreativeTabs[] tmp = new CreativeTabs[index + 1];
			System.arraycopy(CREATIVE_TAB_ARRAY, 0, tmp, 0, CREATIVE_TAB_ARRAY.length);
			CREATIVE_TAB_ARRAY = tmp;
		}
		CREATIVE_TAB_ARRAY[index] = this;
	}
}
