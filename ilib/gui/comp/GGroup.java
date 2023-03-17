package ilib.gui.comp;

import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GGroup extends SimpleComponent implements IGui {
	protected boolean active;

	protected List<Component> components;
	protected final List<Component>[] clicked = GuiHelper.createClickedComponentList();

	public GGroup(IGui parent, int x, int y, int width, int height) {
		super(parent, x, y, width, height);
		this.active = true;

		components = new SimpleList<>();
	}

	public final GGroup append(Component com) {
		components.add(com);
		return this;
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	protected final void superInit() {
		super.onInit();
	}

	@Override
	public void onInit() {
		super.onInit();

		for (int i = 0; i < components.size(); i++) {
			components.get(i).onInit();
		}
	}

	public final void toggleSlot() {
		for (int i = 0; i < components.size(); i++) {
			Object component = components.get(i);
			if (component instanceof GSlot) {
				((GSlot) component).setVisible(isActive());
			}
		}
	}

	@Override
	public void mouseDown(int x, int y, int button) {
		super.mouseDown(x, y, button);

		if (isActive()) {
			x -= xPos;
			y -= yPos;

			List<Component> clicked = this.clicked[button];
			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				if (com.isMouseOver(x, y)) {
					com.mouseDown(x, y, button);
					clicked.add(com);
				}
			}
		}
	}

	@Override
	public void mouseUp(int x, int y, int button) {
		super.mouseUp(x, y, button);

		if (isActive()) {
			x -= xPos;
			y -= yPos;

			List<Component> clicked = this.clicked[button];
			for (int i = 0; i < clicked.size(); i++) {
				clicked.get(i).mouseUp(x, y, button);
			}
			clicked.clear();
		}
	}

	@Override
	public void mouseDrag(int x, int y, int button, long time) {
		super.mouseDrag(x, y, button, time);

		if (isActive()) {
			x -= xPos;
			y -= yPos;

			List<Component> clicked = this.clicked[button];
			for (int i = 0; i < clicked.size(); i++) {
				clicked.get(i).mouseDrag(x, y, button, time);
			}
		}
	}

	@Override
	public void mouseScrolled(int x, int y, int dir) {
		if (!isMouseOver(x, y)) return;
		super.mouseScrolled(x, y, dir);

		if (isActive()) {
			x -= xPos;
			y -= yPos;

			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				com.mouseScrolled(x, y, dir);
			}
		}
	}

	@Override
	public void keyTyped(char letter, int keyCode) {
		if (isActive()) {
			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				com.keyTyped(letter, keyCode);
			}
		}
	}

	@Override
	public void renderTooltip(int relX, int relY, int absX, int absY) {
		super.renderTooltip(relX, relY, absX, absY);

		if (isActive()) {
			relX -= xPos;
			relY -= yPos;

			for (int i = 0; i < components.size(); i++) {
				Component com = components.get(i);
				if (com.isMouseOver(relX, relY)) {
					com.renderTooltip(relX, relY, absX, absY);
				}
			}
		}
	}

	@Override
	public void render(int mouseX, int mouseY) {
		if (isActive()) {
			GlStateManager.pushMatrix();
			GL11.glTranslatef(xPos, yPos, 0);
			GuiHelper.renderBackground(mouseX - xPos, mouseY - yPos, components);
			GlStateManager.popMatrix();
		}
	}

	@Override
	public void render2(int mouseX, int mouseY) {
		if (isActive()) {
			GlStateManager.pushMatrix();
			GL11.glTranslatef(xPos, yPos, 0);
			GuiHelper.renderForeground(mouseX - xPos, mouseY - yPos, components);
			GlStateManager.popMatrix();
		}
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public List<Component> getComponents() {
		return components;
	}

	public void setComponents(List<Component> components) {
		this.components = components;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	@Override
	public TileEntity getTileEntity() {
		return null;
	}

	@Override
	public int getTop() {
		return yPos + owner.getTop();
	}

	@Override
	public int getLeft() {
		return xPos + owner.getLeft();
	}
}
