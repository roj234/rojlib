package ilib.gui.comp;

import ilib.anim.Animation;
import ilib.gui.IGui;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;

import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/7 12:22
 */
public class GPage extends SimpleComponent {
	protected List<Component> components = new SimpleList<>();
	protected int active;
	private boolean clicked;

	public GPage(IGui parent, int x, int y, int w, int h) {
		super(parent, x, y, w, h);
	}

	public final GPage append(Component com) {
		components.add(com);
		return this;
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	@Override
	public void onInit() {
		super.onInit();

		for (int i = 0; i < components.size(); i++) {
			components.get(i).onInit();
		}
	}

	@Override
	public void mouseDown(int x, int y, int button) {
		super.mouseDown(x, y, button);

		x -= xPos;
		y -= yPos;
		Component com = components.get(active);
		if (com.isMouseOver(x, y)) {
			com.mouseDown(x, y, button);
			clicked = true;
		}
	}

	@Override
	public void mouseUp(int x, int y, int button) {
		super.mouseUp(x, y, button);

		x -= xPos;
		y -= yPos;
		if (clicked) {
			Component com = components.get(active);
			com.mouseUp(x, y, button);
			clicked = false;
		}
	}

	@Override
	public void mouseDrag(int x, int y, int button, long time) {
		super.mouseDrag(x, y, button, time);

		x -= xPos;
		y -= yPos;
		if (clicked) components.get(active).mouseDrag(x, y, button, time);
	}

	@Override
	public void mouseScrolled(int x, int y, int dir) {
		super.mouseScrolled(x, y, dir);

		if (!isMouseOver(x, y)) return;

		x -= xPos;
		y -= yPos;
		Component com = components.get(active);
		com.mouseScrolled(x, y, dir);
	}

	@Override
	public void keyTyped(char letter, int keyCode) {
		components.get(active).keyTyped(letter, keyCode);
	}

	@Override
	public void renderTooltip(int relX, int relY, int absX, int absY) {
		super.renderTooltip(relX, relY, absX, absY);

		Component com = components.get(active);
		if (com.isMouseOver(relX -= xPos, relY -= yPos)) {
			com.renderTooltip(relX, relY, absX, absY);
		}
	}

	@Override
	public void render(int mouseX, int mouseY) {
		GlStateManager.pushMatrix();
		GL11.glTranslatef(xPos, yPos, 0);

		Component com = components.get(active);

		Animation anim = com.getAnimation();
		if (anim != null) {
			GlStateManager.pushMatrix();
			anim.apply();
		}
		com.render(mouseX - xPos, mouseY - yPos);
		if (anim != null) {
			GlStateManager.popMatrix();
			if (!anim.isPlaying()) com.setAnimation(null);
		}

		GlStateManager.popMatrix();
	}

	@Override
	public void render2(int mouseX, int mouseY) {
		mouseX -= xPos;
		mouseY -= yPos;

		GlStateManager.pushMatrix();
		GL11.glTranslatef(xPos, yPos, 0);

		Component com = components.get(active);

		Animation anim = com.getAnimation();
		if (anim != null) {
			GlStateManager.pushMatrix();
			anim.apply();
		}
		com.render2(mouseX - xPos, mouseY - yPos);
		if (anim != null) {
			GlStateManager.popMatrix();
			if (!anim.isPlaying()) com.setAnimation(null);
		}

		GlStateManager.popMatrix();
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

	public int getActive() {
		return active;
	}

	public void setActive(int active) {
		components.get(active);
		this.active = active;
	}
}
