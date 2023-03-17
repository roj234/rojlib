package ilib.asm.nx.client;

import ilib.client.CreativeTabsDynamic;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.GTabs;
import ilib.util.MCTexts;
import org.lwjgl.input.Mouse;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.FilterList;
import roj.collect.SimpleList;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.io.IOException;
import java.util.List;


/**
 * @author Roj234
 * @since 2020/9/12 15:11
 */

@Nixim(value = "net.minecraft.client.gui.inventory.GuiContainerCreative", copyItf = true)
//!!AT ["net.minecraft.client.gui.inventory.GuiContainerCreative", ["func_147050_b"], true]
abstract class CustomCreativeTab extends GuiContainerCreative implements IGui {
	@Shadow("field_147058_w")
	static int selectedTabIndex;

	@Shadow(value = "field_147006_u", owner = "net.minecraft.client.gui.inventory.GuiContainer")
	Slot hoveredSlot;

	@Shadow("field_147067_x")
	private float currentScroll;

	@Shadow("field_147062_A")
	private GuiTextField searchField;

	@Copy
	static List<Component> components;

	public CustomCreativeTab(EntityPlayer player) {
		super(player);
	}

	public void setScrollPos(float pos) {
		this.currentScroll = pos;
		((GuiContainerCreative.ContainerCreative) inventorySlots).scrollTo(pos);
	}

	@Copy
	public TileEntity getTileEntity() {
		return null;
	}

	@Override
	@Copy
	public ResourceLocation getTexture() {
		return Component.TEXTURE;
	}

	@Override
	@Inject("func_73864_a")
	protected void mouseClicked(int x, int y, int mouseButton) throws IOException {
		super.mouseClicked(x, y, mouseButton);
		if (components == null) return;

		x -= guiLeft;
		y -= guiTop;
		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com.isMouseOver(x, y)) {
				com.mouseDown(x, y, mouseButton);
			}
		}
	}

	@Override
	@Inject("func_146286_b")
	protected void mouseReleased(int x, int y, int state) {
		super.mouseReleased(x, y, state);
		if (components == null) return;

		x -= guiLeft;
		y -= guiTop;
		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com.isMouseOver(x, y)) {
				com.mouseUp(x, y, state);
			}
		}
	}

	@Shadow(value = "func_146273_a", owner = "net.minecraft.client.gui.inventory.GuiContainer")
	private void shadow0(int a, int b, int c, long d) {}

	@Override
	@Copy(value = "func_146273_a")
	protected void mouseClickMove(int x, int y, int button, long time) {
		shadow0(x, y, button, time);

		if (components == null) return;

		x -= guiLeft;
		y -= guiTop;
		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com.isMouseOver(x, y)) {
				com.mouseDrag(x, y, button, time);
			}
		}
	}

	@Shadow(value = "func_147055_p")
	private boolean needsScrollBars() {
		return false;
	}

	@Shadow(value = "func_146274_d", owner = "net.minecraft.client.gui.inventory.GuiContainer")
	private void handleMouseInput1() throws IOException {}

	@Override
	@Inject("func_146274_d")
	public void handleMouseInput() throws IOException {
		handleMouseInput1();

		if (components == null) return;

		int dir = Mouse.getEventDWheel() / 120;
		if (dir != 0) {
			if (this.needsScrollBars()) {
				int j = (((GuiContainerCreative.ContainerCreative) inventorySlots).itemList.size() + 9 - 1) / 9 - 5;

				this.currentScroll = MathHelper.clamp((float) (currentScroll - (dir / (double) j)), 0.0F, 1.0F);
				((GuiContainerCreative.ContainerCreative) inventorySlots).scrollTo(currentScroll);
			}

			int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
			int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;

			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				com.mouseScrolled(x, y, dir);
			}
		}
	}

	@Override
	@Inject("func_73869_a")
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		super.keyTyped(typedChar, keyCode);
		if (components == null) return;

		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			com.keyTyped(typedChar, keyCode);
		}
	}

	@Inject("func_146979_b")
	protected void drawGuiContainerForegroundLayer(int x, int y) {
		super.drawGuiContainerForegroundLayer(x, y);

		if (components == null) return;
		GuiHelper.renderForeground(x - guiLeft, x - guiTop, components);
	}

	@Inject("func_146976_a")
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int x, int y) {
		super.drawGuiContainerBackgroundLayer(partialTicks, x, y);

		if (components == null) return;

		GlStateManager.pushMatrix();
		GlStateManager.translate(guiLeft, guiTop, 0);

		GuiHelper.renderBackground(x - guiLeft, x - guiTop, components);

		GlStateManager.popMatrix();
	}

	@Inject("func_147050_b")
	public void setCurrentCreativeTab(CreativeTabs tab) {
		super.setCurrentCreativeTab(tab);

		if (tab != CreativeTabs.SEARCH) {
			searchField.setFocused(false);
			searchField.setCanLoseFocus(true);
		}

		if (components == null) {components = new SimpleList<>();} else components.clear();

		if (tab instanceof CreativeTabsDynamic) {
			((CreativeTabsDynamic) tab).addComponents(this, components);
		}
	}

	@Copy(value = "func_191948_b")
	protected void drawHoveredTooltip(int x, int y) {
		if (this.mc.player.inventory.getItemStack().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.getHasStack()) {
			this.renderToolTip(this.hoveredSlot.getStack(), x, y);
		} else {
			if (components == null) return;

			x -= guiLeft;
			y -= guiTop;
			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				if (com.isMouseOver(x, y)) {
					com.renderTooltip(x, y, x + guiLeft, y + guiTop);
				}
			}
		}
	}

	@Inject("func_147053_i")
	private void updateCreativeSearch() {
		GuiContainerCreative.ContainerCreative container = (GuiContainerCreative.ContainerCreative) this.inventorySlots;
		final NonNullList<ItemStack> list = container.itemList;

		list.clear();

		CreativeTabs tab = CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex];

		final String text = this.searchField.getText().toLowerCase();

		if (tab != CreativeTabs.SEARCH) {
			if (!text.isEmpty()) {
				NonNullList<ItemStack> checker = new NonNullList<>(new FilterList<>((old, latest) -> {
					boolean matches = false;

					List<String> inf = latest.getTooltip(mc.player, ITooltipFlag.TooltipFlags.ADVANCED);
					for (int i = 0; i < inf.size(); i++) {
						String line = inf.get(i);
						if (containsPinyin(line.toLowerCase(), text)) {
							matches = true;
							break;
						}
					}

					if (matches) {
						list.add(latest);
					}

					return false;
				}), null);

				tab.displayAllRelevantItems(checker);
			} else {
				tab.displayAllRelevantItems(list);
			}
		} else {
			if (text.isEmpty()) {
				for (Item item : Item.REGISTRY) {
					item.getSubItems(CreativeTabs.SEARCH, list);
				}
			} else {
				list.addAll(this.mc.getSearchTree(SearchTreeManager.ITEMS).search(text));
			}
		}

		this.currentScroll = 0.0F;
		container.scrollTo(0.0F);
	}

	@Copy
	private static boolean containsPinyin(String line, String text) {
		return line.contains(text) || MCTexts.pinyin().toPinyin(line).contains(text);
	}

	@Override
	@Copy
	public void componentRequestUpdate() {
		setCurrentCreativeTab(CreativeTabs.CREATIVE_TAB_ARRAY[getSelectedTabIndex()]);
	}

	@Override
	public List<Rectangle> getCoveredAreas(List<Rectangle> areas) {
		areas.add(new Rectangle(guiLeft, guiTop, xSize, ySize));
		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com instanceof GTabs) {
				((GTabs) com).getAreasCovered(guiLeft, guiTop, areas);
			} else {
				areas.add(new Rectangle(com.getArea(guiLeft, guiTop)));
			}
		}
		return areas;
	}

	@Override
	@Copy
	public int getLeft() {
		return guiLeft;
	}

	@Override
	@Copy
	public int getTop() {
		return guiTop;
	}

	@Override
	@Copy
	public int getWidth() {
		return xSize;
	}

	@Override
	@Copy
	public int getHeight() {
		return ySize;
	}

}
