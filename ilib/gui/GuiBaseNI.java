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
import ilib.gui.comp.GPopup;
import ilib.gui.comp.GTabs;
import ilib.gui.util.GuiListener;
import org.lwjgl.input.Mouse;
import roj.collect.SimpleList;
import roj.reflect.ReflectionUtils;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.IDisplayableError;
import net.minecraftforge.fml.common.IFMLHandledException;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public abstract class GuiBaseNI extends GuiScreen implements IGui {
	protected int xSize, ySize, flag;

	protected int texX = 256, texY = 256;
	public ResourceLocation tex;

	protected int guiLeft, guiTop;

	protected GuiScreen prevScreen;

	protected GuiListener listener;

	protected List<Component> components = new SimpleList<>();
	private final List<Component>[] clicked = GuiHelper.createClickedComponentList();
	private List<Component> prev;

	protected void setImageSize(int w, int h) {
		this.texX = w;
		this.texY = h;
	}

	public GuiBaseNI(int width, int height, ResourceLocation texture) {
		this.xSize = width;
		this.ySize = height;
		this.tex = texture;
	}

	@Override
	public void initGui() {
		if (xSize < 0 || (flag & 1) != 0) {
			guiLeft = 0;
			xSize = width;
			flag |= 1;
		} else {
			guiLeft = (width - xSize) / 2;
		}

		if (ySize < 0 || (flag & 2) != 0) {
			guiTop = 0;
			ySize = height;
			flag |= 2;
		} else {
			guiTop = (height - ySize) / 2;
		}

		prev = null;

		init();

		for (int i = 0; i < components.size(); i++) {
			components.get(i).onInit();
		}
	}

	protected void init() {
		components.clear();
		addComponents();
	}

	protected abstract void addComponents();

	public final void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
		if (texX == 256 && texY == 256) {
			if (w > 256 || h > 256) {drawScaledCustomSizeModalRect(x, y, u, v, w, h, 256, 256, 256, 256);} else RenderUtils.fastRect(x, y, u, v, w, h);
		} else {
			RenderUtils.fastRect(x, y, u, v, w, h, texX, texY);
		}
	}

	public TileEntity getTileEntity() {
		return null;
	}

	@Override
	public ResourceLocation getTexture() {
		return this.tex;
	}

	protected GPopup addPopup(String title, String content, List<String> buttons, int markBegin) {
		if (prev == null) {
			prev = new SimpleList<>(components);
			components.clear();
		}

		for (int i = components.size() - 1; i >= 0; i--) {
			Component c = components.get(i);
			if (c instanceof GPopup) {
				GPopup popup = (GPopup) c;
				if (title.equals(popup.getTitle())) {
					return popup;
				}
			}
		}
		GPopup popup = new GPopup(this, title, content, buttons, markBegin);
		components.add(popup);
		popup.onInit();
		return popup;
	}

	protected GPopup closePopup(String title) {
		for (int i = components.size() - 1; i >= 0; i--) {
			Component c = components.get(i);
			if (c instanceof GPopup) {
				GPopup popup = (GPopup) c;
				if (title == null || title.equals(popup.getTitle())) {
					components.remove(i);
					if (components.isEmpty() && prev != null) {
						components = prev;
						prev = null;
					}
					return popup;
				}
			}
		}
		return null;
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
	protected void keyTyped(char typedChar, int keyCode) {
		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			com.keyTyped(typedChar, keyCode);
		}

		if (keyCode == 1) {
			mc.displayGuiScreen(prevScreen);
			if (mc.currentScreen == null) {
				mc.setIngameFocus();
			}
		}
	}

	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

	protected void drawBackgroundImage() {
		drawWorldBackground(0);

		if (tex != Component.TEXTURE && tex != null) {
			RenderUtils.bindTexture(tex);
			RenderUtils.fastRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		}

		MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.BackgroundDrawnEvent(this));
	}

	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void drawScreen(int x, int y, float partialTicks) {
		GlStateManager.pushMatrix();

		if (prev == null) drawBackgroundImage();

		GlStateManager.translate(guiLeft, guiTop, 0);
		GlStateManager.enableDepth();

		x -= guiLeft;
		y -= guiTop;

		GuiHelper.renderBackground(x, y, components);

		for (int i = 0; i < components.size(); i++) {
			Component com = components.get(i);
			if (com.isMouseOver(x, y)) {
				com.renderTooltip(x, y, x, y);
			}
		}

		this.drawGuiContainerForegroundLayer(x, y);
		GuiHelper.renderForeground(x, y, components);

		GlStateManager.popMatrix();
	}

	@Override
	public int getLeft() {
		return guiLeft;
	}

	@Override
	public int getTop() {
		return guiTop;
	}

	@Override
	public int getWidth() {
		return xSize;
	}

	@Override
	public int getHeight() {
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

	public void throwAsException() {
		try {
			ReflectionUtils.setValue(FMLClientHandler.instance(), FMLClientHandler.class, "errorToDisplay", new AsException());
			MinecraftForge.EVENT_BUS.shutdown();
		} catch (Exception e) {
			throw new AsException();
		}
	}

	private class AsException extends RuntimeException implements IDisplayableError, IFMLHandledException {
		@Override
		public Throwable fillInStackTrace() {
			return this;
		}

		@Override
		public GuiScreen createGui() {
			return GuiBaseNI.this;
		}
	}

}
