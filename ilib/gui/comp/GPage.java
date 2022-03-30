package ilib.gui.comp;

import ilib.client.RenderUtils;
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

    public GPage(IGui parent, int x, int y, int w, int h) {
        super(parent, x, y, w, h);
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    public final GPage append(Component com) {
        components.add(com);
        return this;
    }

    public List<Component> getComponents() {
        return components;
    }

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
        }
    }

    @Override
    public void mouseUp(int x, int y, int button) {
        super.mouseUp(x, y, button);

        x -= xPos;
        y -= yPos;
        Component com = components.get(active);
        if (com.isMouseOver(x, y)) {
            com.mouseUp(x, y, button);
        }
    }

    @Override
    public void mouseDrag(int x, int y, int button, long time) {
        super.mouseDrag(x, y, button, time);

        x -= xPos;
        y -= yPos;
        components.get(active).mouseDrag(x, y, button, time);
    }

    @Override
    public void mouseScrolled(int x, int y, int dir) {
        if(!isMouseOver(x, y)) return;

        x -= xPos;
        y -= yPos;
        Component com = components.get(active);
        com.mouseScrolled(x, y, dir);
    }

    @Override
    public void keyTyped(char letter, int keyCode) {
        Component com = components.get(active);
        com.keyTyped(letter, keyCode);
    }

    @Override
    public void renderToolTip(int x, int y) {
        x -= xPos;
        y -= yPos;

        Component com = components.get(active);
        if (com.isMouseOver(x, y)) {
            GlStateManager.pushMatrix();
            GL11.glTranslatef(xPos, yPos, 0);

            com.renderToolTip(x, y);

            GlStateManager.popMatrix();
        }
    }

    @Override
    public final void render(int mouseX, int mouseY) {
        mouseX -= xPos;
        mouseY -= yPos;

        GlStateManager.pushMatrix();
        GL11.glTranslatef(xPos, yPos, 0);

        Component com = components.get(active);

        RenderUtils.prepareRenderState();
        com.render(mouseX, mouseY);
        RenderUtils.restoreRenderState();

        GlStateManager.popMatrix();
    }

    @Override
    public final void renderOverlay(int mouseX, int mouseY) {
        mouseX -= xPos;
        mouseY -= yPos;

        GlStateManager.pushMatrix();
        GL11.glTranslatef(xPos, yPos, 0);

        Component com = components.get(active);

        RenderUtils.prepareRenderState();
        com.renderOverlay(mouseX, mouseY);
        RenderUtils.restoreRenderState();

        GlStateManager.popMatrix();
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public int getActive() {
        return active;
    }

    public void setActive(int active) {
        components.get(active);
        this.active = active;
    }
}
