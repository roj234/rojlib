package ilib.client;

import ilib.api.client.FakeTab;
import ilib.gui.DefaultSprites;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.GButton;
import ilib.gui.comp.GButtonNP;
import ilib.gui.comp.SimpleComponent;
import ilib.util.MCTexts;
import roj.collect.SimpleList;
import roj.math.MathUtils;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/13 12:42
 */
public class CreativeTabsDynamic extends CreativeTabsMy {
	private class UpDownButton extends GButton {
		public UpDownButton(IGui parent, boolean up) {
			super(parent, -22, up ? 8 : 128, DefaultSprites.UP_BTN);
		}

		@Override
		protected void doAction() {
			int nOff = MathUtils.clamp(offset + (yPos == 8 ? -1 : 1), 0, Math.max(categories.size() - 4, 0));
			if (nOff != offset) {
				offset = nOff;
				updateButton();
				GuiHelper.playButtonSound();
			}
		}
	}

	private class TabSelectButton extends GButtonNP {
		private CreativeTabs tab;

		public TabSelectButton(IGui parent, int yPos, CreativeTabs tab) {
			super(parent, -26, yPos, 22, 22);
			setDummy();
			setTab(tab);
		}

		protected void setTab(@Nonnull CreativeTabs tab) {
			this.tab = tab;
			setLabel(tab.getIcon());
			setToggled(selected == tab);
		}

		@Override
		public void getDynamicTooltip(List<String> tooltip, int mouseX, int mouseY) {
			tooltip.add(MCTexts.format(tab.getTranslationKey()));
		}

		@Override
		protected void doAction() {
			if (selected != tab) {
				selected = tab;
				GuiHelper.playButtonSound();
				owner.componentRequestUpdate();
			}
		}
	}

	private class ScrollHandler extends SimpleComponent {
		public ScrollHandler(IGui parent) {
			super(parent, -26, 20, 24, 100);
		}

		@Override
		public void mouseScrolled(int x, int y, int dir) {
			if (!checkMouseOver(x, y)) return;

			int nOff = MathUtils.clamp(offset - dir, 0, Math.max(categories.size() - 4, 0));
			if (nOff != offset) {
				offset = nOff;
				updateButton();
			}
		}
	}

	@Override
	public boolean hasSearchBar() {
		return selected != null && selected.hasSearchBar();
	}

	public static final class Category extends CreativeTabsMy implements FakeTab {
		public Category(String name) {
			super(name);
		}

		public Category appendTo(CreativeTabsDynamic ct) {
			ct.categories.add(this);
			if (ct.selected == null) {
				ct.selected = this;
			}
			return this;
		}
	}

	protected final List<CreativeTabs> categories;
	protected CreativeTabs selected;
	protected int offset;

	protected List<Component> components;

	public CreativeTabsDynamic(String name) {
		super(name);
		this.categories = new SimpleList<>();
	}

	@Override
	public void displayAllRelevantItems(NonNullList<ItemStack> list) {
		for (Item item : Item.REGISTRY) {
			if (selected == item.getCreativeTab()) item.getSubItems(item.getCreativeTab(), list);
		}
	}

	public void addComponents(IGui parent, List<Component> list) {
		components = list;
		offset = MathUtils.clamp(offset, 0, Math.max(categories.size() - 4, 0));

		list.add(new UpDownButton(parent, true));
		list.add(new UpDownButton(parent, false));
		list.add(new ScrollHandler(parent));

		int yPos = 22;

		for (int i = offset, l = Math.min(categories.size(), offset + 4); i < l; i++) {
			CreativeTabs tabs = categories.get(i);
			list.add(new TabSelectButton(parent, yPos, tabs));

			yPos += 24;
		}

	}

	protected void updateButton() {
		for (int i = offset, j = 3, l = Math.min(categories.size(), offset + 4); i < l; i++, j++) {
			TabSelectButton button = (TabSelectButton) components.get(j);
			button.setTab(categories.get(i));
		}
	}
}
