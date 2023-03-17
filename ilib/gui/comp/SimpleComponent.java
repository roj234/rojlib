package ilib.gui.comp;

import ilib.gui.IGui;
import ilib.gui.util.SizeModifiable;

/**
 * @since 2021/1/13 12:37
 */
public abstract class SimpleComponent extends Component implements SizeModifiable {
	protected int width, height;

	public SimpleComponent(IGui parent) {
		super(parent);
	}

	public SimpleComponent(IGui parent, int x, int y) {
		super(parent, x, y);
	}

	public SimpleComponent(IGui parent, int x, int y, int w, int h) {
		super(parent, x, y);
		this.width = w;
		this.height = h;
	}

	@Override
	public void onInit() {
		if (xPos < 0) xPos += owner.getWidth();
		if (yPos < 0) yPos += owner.getHeight();
		if (width < 0) width = owner.getWidth() + width - xPos;
		if (height < 0) height = owner.getHeight() + height - yPos;

		super.onInit();
	}

	protected final boolean shouldInit() {
		return ((xPos | yPos | width | height) & 0x80000000) != 0;
	}

	@Override
	public void render(int mouseX, int mouseY) {}

	@Override
	public void render2(int mouseX, int mouseY) {}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public void setHeight(int height) {
		this.height = height;
	}
}
