/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GuiBase.java
 */
package ilib.gui;

import ilib.client.RenderUtils;
import ilib.gui.comp.Component;
import ilib.gui.comp.GTabs;
import ilib.gui.util.GuiListener;
import org.lwjgl.input.Mouse;
import roj.collect.SimpleList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public abstract class GuiBase<T extends ContainerIL> extends GuiContainer implements IGui {
	protected String name;

	protected T inventory;

	public ResourceLocation tex;

	protected GuiListener listener;

	protected List<Component> components = new SimpleList<>();
	private final List<Component>[] clicked = GuiHelper.createClickedComponentList();

	public GuiBase(T inventory, int width, int height, String title, ResourceLocation texture) {
		super(inventory);
		this.xSize = width;
		this.ySize = height;
		this.name = title;
		this.tex = texture;
		this.inventory = inventory;
	}

	public GuiBase(T inventory) {
		super(inventory);
		this.inventory = inventory;
	}

	@Override
	public final void initGui() {
		super.initGui();

		init();

		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			com.onInit();
		}
	}

	protected void init() {
		components.clear();
		addComponents();
	}

	protected abstract void addComponents();

	@Override
	protected void renderHoveredToolTip(int mouseX, int mouseY) {
		mouseX -= guiLeft;
		mouseY -= guiTop;

		if (mc.player.inventory.getItemStack().isEmpty() && hoveredSlot != null && hoveredSlot.getHasStack()) {
			this.renderToolTip(hoveredSlot.getStack(), mouseX, mouseY);
		}

		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com.isMouseOver(mouseX, mouseY)) {
				com.renderTooltip(mouseX, mouseY, mouseX, mouseY);
			}
		}
	}

	@Override
	public TileEntity getTileEntity() { // slow
		return null;
	}

	@Override
	public final ResourceLocation getTexture() {
		return this.tex;
	}

	@Override
	protected void mouseClicked(int x, int y, int button) throws IOException {
		super.mouseClicked(x, y, button);

		x -= guiLeft;
		y -= guiTop;

		if (listener != null) listener.mouseDown(x, y, button);

		List<Component> clicked = this.clicked[button];
		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com.isMouseOver(x, y)) {
				com.mouseDown(x, y, button);
				clicked.add(com);
			}
		}
	}

	@Override
	protected void mouseReleased(int x, int y, int button) {
		super.mouseReleased(x, y, button);

		x -= guiLeft;
		y -= guiTop;

		if (listener != null) listener.mouseUp(x, y, button);

		List<Component> clicked = this.clicked[button];
		for (int i = 0; i < clicked.size(); i++) {
			clicked.get(i).mouseUp(x, y, button);
		}
		clicked.clear();
	}

	@Override
	protected void mouseClickMove(int x, int y, int button, long time) {
		x -= guiLeft;
		y -= guiTop;

		if (listener != null) listener.mouseDrag(x, y, button, time);

		List<Component> clicked = this.clicked[button];
		for (int i = 0; i < clicked.size(); i++) {
			clicked.get(i).mouseDrag(x, y, button, time);
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();

		int dir = Mouse.getEventDWheel();
		if (dir != 0) {
			int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
			int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;

			if (listener != null) listener.mouseScrolled(x, y, dir);

			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				com.mouseScrolled(x, y, dir / 120);
			}
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		super.keyTyped(typedChar, keyCode);

		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			com.keyTyped(typedChar, keyCode);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		GuiHelper.renderForeground(mouseX - guiLeft, mouseY - guiTop, components);

		renderHoveredToolTip(mouseX, mouseY);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		drawBackgroundImage();

		GlStateManager.pushMatrix();
		GlStateManager.translate(guiLeft, guiTop, 0);

		GuiHelper.renderBackground(mouseX - guiLeft, mouseY - guiTop, components);

		GlStateManager.popMatrix();
	}

	protected void drawBackgroundImage() {
		drawWorldBackground(0);

		if (tex != null) {
			RenderUtils.bindTexture(tex);
			RenderUtils.fastRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		}

		MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.BackgroundDrawnEvent(this));
	}

	public final int getLeft() {
		return guiLeft;
	}

	public final int getTop() {
		return guiTop;
	}

	public final int getWidth() {
		return xSize;
	}

	public final int getHeight() {
		return ySize;
	}

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

	public GuiListener getListener() {
		return listener;
	}

	public void setListener(GuiListener listener) {
		this.listener = listener;
	}
}
