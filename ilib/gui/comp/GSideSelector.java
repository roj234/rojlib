package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import ilib.gui.util.SidePicker;
import ilib.gui.util.TrackballMC;
import org.lwjgl.opengl.GL11;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author Roj234
 * @since 2021/5/31 20:23
 */
public class GSideSelector extends Component {
	protected Provider provider;

	protected float scale;
	protected int diameter;

	protected final TrackballMC trackball;
	protected IBlockState state;

	protected boolean highlight;
	protected final SidePicker picker;
	protected EnumFacing lastHit;

	public GSideSelector(IGui parent, int x, int y, float scale, @Nullable IBlockState state, boolean highlightWhenNoHover) {
		super(parent, x, y);
		this.scale = scale;
		this.state = state;
		this.highlight = highlightWhenNoHover;

		this.diameter = MathHelper.ceil(scale * Math.sqrt(3));
		trackball = new TrackballMC((int) scale, ClientProxy.mc.gameSettings.mouseSensitivity * 2);

		picker = new SidePicker(0.5);
	}

	@Override
	public void onInit() {
		super.onInit();

		Entity entity = ClientProxy.mc.getRenderViewEntity();
		if (entity != null) trackball.setTransform(RenderUtils.createEntityRotateMatrix(entity));
	}

	protected void onSideToggled(EnumFacing side, int modifier, int button) {
		provider.onSideToggled(this, side, modifier, button);
	}

	@Nullable
	protected Color getColorForSide(EnumFacing side) {
		return provider.getColorForSide(this, side);
	}

	@Override
	public void mouseDown(int x, int y, int button) {
		super.mouseDown(x, y, button);
		lastHit = null;
		switch (button) {
			case GuiHelper.MIDDLE:
				onInit();
				break;
			case GuiHelper.RIGHT:
				trackball.startDrag(x - xPos - diameter / 2, y - yPos - diameter / 2);
				break;
		}
	}

	@Override
	public void mouseUp(int x, int y, int button) {
		super.mouseUp(x, y, button);

		switch (button) {
			case GuiHelper.LEFT:
				if (lastHit != null) onSideToggled(lastHit, (GuiHelper.isShiftPressed() ? 1 : 0) | (GuiHelper.isCtrlPressed() ? 2 : 0), button);
				break;
			case GuiHelper.RIGHT:
				trackball.endDrag(x - xPos - diameter / 2, y - yPos - diameter / 2);
				break;
		}
	}

	@Override
	public void render(int mouseX, int mouseY) {
		int w = diameter / 2;

		GlStateManager.pushMatrix();
		GlStateManager.color(1, 0, 0, 0.4f);
		GlStateManager.translate(xPos + w, yPos + w, diameter);
		GlStateManager.scale(scale, -scale, scale);

		trackball.applyTransform(mouseX - xPos - w, mouseY - yPos - w);

		if (state != null) {
			RenderUtils.bindMinecraftBlockSheet();
			RenderUtils.renderBlock(null, BlockPos.ORIGIN, state);
		}

		Color[] colors = new Color[highlight ? 7 : 1];

		SidePicker.Hit hit = picker.getNearestHit();

		if (hit != null) {colors[0] = getColorForSide(lastHit = hit.side);} else lastHit = null;

		if (highlight) {
			EnumFacing[] values = EnumFacing.VALUES;
			int i = 0;
			while (i < values.length) {
				EnumFacing dir = values[i];
				colors[++i] = getColorForSide(dir);
			}
		}

		drawHighlights(lastHit, colors);

		GlStateManager.popMatrix();
	}

	@Override
	public void render2(int mouseX, int mouseY) {}

	/*******************************************************************************************************************
	 * Utility methods                                                                                                 *
	 *******************************************************************************************************************/

	// for override
	protected void drawHighlights(EnumFacing face, Color[] colors) {
		EnumFacing[] values = EnumFacing.VALUES;

		Boolean blendState = null;
		for (int i = 0; i < colors.length; i++) {
			Color c = colors[i];
			if (c == null || c.getAlpha() == 0) continue;

			EnumFacing dir = i == 0 ? face : values[i - 1];

			if (dir != null) {
				if (blendState == null) {
					blendState = GL11.glGetBoolean(GL11.GL_BLEND);
					prepareRenderState();
				}

				RenderUtils.setColor(c);
				drawQuad(dir);
			}
		}

		if (blendState != null) {
			GL11.glEnd();

			GlStateManager.enableTexture2D();
			if (!blendState) {
				GlStateManager.disableBlend();
			}
		}
	}

	protected static void prepareRenderState() {
		GlStateManager.disableTexture2D();

		GL11.glBegin(GL11.GL_QUADS);
	}

	protected static void restoreRenderState() {}

	protected static void drawQuad(EnumFacing dir) {
		switch (dir) {
			case EAST:
				GL11.glVertex3f(0.51f, -0.51f, -0.51f);
				GL11.glVertex3f(0.51f, 0.51f, -0.51f);
				GL11.glVertex3f(0.51f, 0.51f, 0.51f);
				GL11.glVertex3f(0.51f, -0.51f, 0.51f);
				break;
			case UP:
				GL11.glVertex3f(-0.51f, 0.51f, -0.51f);
				GL11.glVertex3f(-0.51f, 0.51f, 0.51f);
				GL11.glVertex3f(0.51f, 0.51f, 0.51f);
				GL11.glVertex3f(0.51f, 0.51f, -0.51f);
				break;
			case SOUTH:
				GL11.glVertex3f(-0.51f, -0.51f, 0.51f);
				GL11.glVertex3f(0.51f, -0.51f, 0.51f);
				GL11.glVertex3f(0.51f, 0.51f, 0.51f);
				GL11.glVertex3f(-0.51f, 0.51f, 0.51f);
				break;
			case WEST:
				GL11.glVertex3f(-0.51f, -0.51f, -0.51f);
				GL11.glVertex3f(-0.51f, -0.51f, 0.51f);
				GL11.glVertex3f(-0.51f, 0.51f, 0.51f);
				GL11.glVertex3f(-0.51f, 0.51f, -0.51f);
				break;
			case DOWN:
				GL11.glVertex3f(-0.51f, -0.51f, -0.51f);
				GL11.glVertex3f(0.51f, -0.51f, -0.51f);
				GL11.glVertex3f(0.51f, -0.51f, 0.51f);
				GL11.glVertex3f(-0.51f, -0.51f, 0.51f);
				break;
			case NORTH:
				GL11.glVertex3f(-0.51f, -0.51f, -0.51f);
				GL11.glVertex3f(-0.51f, 0.51f, -0.51f);
				GL11.glVertex3f(0.51f, 0.51f, -0.51f);
				GL11.glVertex3f(0.51f, -0.51f, -0.51f);
				break;
			default:
		}
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	@Override
	public int getWidth() {
		return diameter;
	}

	@Override
	public int getHeight() {
		return diameter;
	}

	public float getScale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = scale;
		this.diameter = MathHelper.ceil(scale * Math.sqrt(3));
	}

	public IBlockState getBlockState() {
		return state;
	}

	public void setBlockState(IBlockState blockState) {
		this.state = blockState;
	}

	public boolean isHighlight() {
		return highlight;
	}

	public void setHighlight(boolean highlight) {
		this.highlight = highlight;
	}

	public Provider getProvider() {
		return provider;
	}

	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public interface Provider {
		/**
		 * 0 : Normal Click (to the next mode)
		 * 1 : Shift Click (to default or disabled)
		 * 2 : Control Click (backward)
		 */
		void onSideToggled(GSideSelector sel, EnumFacing side, int modifier, int button);

		/**
		 * As a general rule:
		 * RED     : Disabled
		 * BLUE    : Input
		 * ORANGE  : Output
		 * GREEN   : Both
		 */
		@Nullable
		default Color getColorForSide(GSideSelector sel, EnumFacing side) {
			return null;
		}
	}
}
