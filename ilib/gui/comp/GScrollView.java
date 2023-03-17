package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.DefaultSprites;
import ilib.gui.IGui;
import ilib.gui.util.Direction;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;
import roj.math.MathUtils;

import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

/**
 * @author solo6975
 * @since 2022/4/1 13:14
 */
public abstract class GScrollView extends GGroup {
	protected final class ScrollBar extends SimpleComponent {
		public ScrollBar() {
			super(GScrollView.this, 8, 8, 0, 0);
			switch (direction) {
				case UP:
					yPos = 0;
					width = GScrollView.this.getWidth() - 16;
					height = 14;
					break;
				case DOWN:
					yPos = GScrollView.this.getHeight() - 14;
					width = GScrollView.this.getWidth() - 16;
					height = 14;
					break;
				case LEFT:
					xPos = 0;
					width = 14;
					height = GScrollView.this.getHeight() - 16;
					break;
				case RIGHT:
					xPos = GScrollView.this.getWidth() - 14;
					width = 14;
					height = GScrollView.this.getHeight() - 16;
					break;
			}
		}

		@Override
		public void mouseDown(int x, int y, int button) {
			super.mouseDown(x, y, button);

			if (button == 0) scroll(x, y);
		}

		@Override
		public void mouseScrolled(int x, int y, int dir) {
			super.mouseScrolled(x, y, dir);
			setOffset(off - dir * step);
		}

		@Override
		public void mouseDrag(int x, int y, int button, long time) {
			super.mouseDrag(x, y, button, time);

			if (button == 0) scroll(x, y);
		}

		private void scroll(int x, int y) {
			float percent = (direction.isVertical() ? (float) (x - 7) / width : (float) (y - 7) / height);
			scrollTo(percent, false);
		}

		@Override
		public void render(int mouseX, int mouseY) {
			if (getElementCount() <= getViewCapacity() && !alwaysShow) return;

			GlStateManager.disableTexture2D();

			RenderUtils.drawRectangle(xPos, yPos, 0, width, height, background.getRGB());

			float capacity = GScrollView.this.getViewCapacity();
			float percent = (float) off / (getElementCount() - (float) Math.round(capacity));

			capacity /= getElementCount();
			if (capacity >= 1 || percent != percent) {
				GlStateManager.enableTexture2D();
				return;
			}

			float x, y, w, h;
			// 实际上这个与滚动条相反
			if (direction.isVertical()) {
				w = width * capacity;
				h = height;
				x = xPos + percent * (width - w);
				y = yPos;
			} else {
				w = width;
				h = height * capacity;
				x = xPos;
				y = yPos + percent * (height - h);
			}
			// 1px margin
			RenderUtils.drawRectangle(x + 1, y + 1, 1, w - 2, h - 2, foreground.getRGB());

			GlStateManager.enableTexture2D();
		}
	}

	protected final class ScrollBtn extends GButton {
		private int overTime;

		public ScrollBtn(boolean up) {
			super(GScrollView.this, 0, 0, DefaultSprites.UP_BTN);
			setFlag(BUTTON_ENABLED | CHANGE_V_BY_HOVER);

			if (up) flag |= 256;

			switch (direction) {
				case UP:
					xPos = up ? 0 : GScrollView.this.getWidth() - 8;
					yPos = 0;
					width = 8;
					height = 14;
					break;
				case DOWN:
					xPos = up ? 0 : GScrollView.this.getWidth() - 8;
					yPos = GScrollView.this.getHeight() - 14;
					width = 8;
					height = 14;
					break;
				case LEFT:
					xPos = 0;
					yPos = up ? 0 : GScrollView.this.getHeight() - 8;
					break;
				case RIGHT:
					xPos = GScrollView.this.getWidth() - 14;
					yPos = up ? 0 : GScrollView.this.getHeight() - 8;
					break;
			}
		}

		@Override
		@SuppressWarnings("fallthrough")
		public void render(int mouseX, int mouseY) {
			if (getElementCount() <= getViewCapacity()) {
				if (!alwaysShow) return;
				flag &= ~(BUTTON_CLICKING | BUTTON_HOVERED);
			} else {
				super.isMouseOver(mouseX, mouseY);
			}

			if (isClicking()) {
				overTime++;
			} else {
				overTime = 0;
			}

			GlStateManager.pushMatrix();

			GL11.glTranslatef(xPos, yPos, 0);
			int x = 0, y = 0, w = width, h = height;
			if (direction.isVertical()) {
				if (flag >= 256) {
					x = -h;
					GL11.glRotatef(-90, 0, 0, 1);
				} else {
					y = -w;
					GL11.glRotatef(90, 0, 0, 1);
				}
				w = height;
				h = width;
			} else if (flag < 256) {
				x = -w;
				y = -h;
				GL11.glRotatef(180, 0, 0, 1);
			}

			RenderUtils.bindTexture(texture);
			drawTexturedModalRect(x, y, u, isHovered() ? v + h : v, w, h);

			GlStateManager.popMatrix();

			if (overTime > 30) setOffset(off + (flag >= 256 ? -1 : 1));
		}

		@Override
		protected void doAction() {
			setOffset(off + (flag >= 256 ? -step : step));
		}
	}

	public static Color DEFAULT_BACKGROUND = new Color(0x2c2c2c);
	public static Color DEFAULT_FOREGROUND = new Color(0xc6c6c6);

	protected boolean alwaysShow;
	protected int off, step, reserved;
	protected Direction direction;

	protected Color background, foreground;

	public GScrollView(IGui parent, int x, int y, int width, int height) {
		super(parent, x, y, width, height);
		active = true;
		background = DEFAULT_BACKGROUND;
		foreground = DEFAULT_FOREGROUND;
		step = 2;
		reserved = 3;

		setDirectionAndInit(Direction.RIGHT);
	}

	@Override
	public void onInit() {
		super.onInit();
		if (off < 0) setDirectionAndInit(direction);
	}

	protected abstract void addElements(int from, int to);

	protected abstract int getElementCount();

	protected abstract int getElementLength();

	public GScrollView setDirectionAndInit(Direction d) {
		direction = d;
		off = -1;
		if (shouldInit()) return this;

		components.clear();
		components.add(new ScrollBtn(true));
		components.add(new ScrollBtn(false));
		components.add(new ScrollBar());

		setOffset(0);
		return this;
	}

	public void refresh() {
		int off1 = off;
		off = -1;
		setOffset(off1);
	}

	public void setOffset(int offset) {
		int displayable = Math.round(getViewCapacity());

		int i = MathUtils.clamp(offset, 0, Math.max(getElementCount() - displayable, 0));

		if (i == off) return;

		((SimpleList<Component>) components).removeRange(reserved, components.size());

		addElements(i, Math.min(getElementCount(), i + displayable));

		for (int j = reserved; j < components.size(); j++) {
			components.get(j).onInit();
		}

		this.off = i;
	}

	public int getOffset() {
		return off;
	}

	public void scrollTo(float percent, boolean force) {
		int halfDisplayable = Math.round(getViewCapacity() / 2);
		if (force) off = -1;
		setOffset(Math.round(percent * getElementCount()) - halfDisplayable);
	}

	/**
	 * 计算滚动条宽度的, 覆盖于竖向排列和横向滚动条或反之的情况
	 */
	public float getViewCapacity() {
		return (float) (direction.isVertical() ? width : height) / getElementLength();
	}

	public boolean isAlwaysShow() {
		return alwaysShow;
	}

	public void setAlwaysShow(boolean alwaysShow) {
		this.alwaysShow = alwaysShow;
	}

	public int getReservedComponentCount() {
		return reserved;
	}

	public void setReservedComponentCount(int reserved) {
		this.reserved = reserved;
	}

	public int getStep() {
		return step;
	}

	public void setStep(int step) {
		this.step = step;
	}

	public Color getBackground() {
		return background;
	}

	public void setBackground(Color background) {
		this.background = background;
	}

	public Color getForeground() {
		return foreground;
	}

	public void setForeground(Color foreground) {
		this.foreground = foreground;
	}
}
