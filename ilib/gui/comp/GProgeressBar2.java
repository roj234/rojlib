package ilib.gui.comp;

import ilib.gui.IGui;
import ilib.gui.util.Direction;
import roj.util.Int2IntFunction;

import java.util.List;
import java.util.function.Consumer;

@Deprecated
public final class GProgeressBar2 extends GProgressBar {
	private final Int2IntFunction function;
	private Consumer<List<String>> tooltip;

	public GProgeressBar2(IGui parent, int x, int y, int texU, int texV, int imageWidth, int imageHeight, Direction dir, Int2IntFunction function) {
		super(parent, x, y, texU, texV, imageWidth, imageHeight, dir);
		this.function = function;
	}

	public GProgeressBar2(IGui parent, int x, int y, int texU, int texV, int imageWidth, int imageHeight, Direction dir, Int2IntFunction function, Consumer<List<String>> tooltip) {
		super(parent, x, y, texU, texV, imageWidth, imageHeight, dir);
		this.function = function;
		this.tooltip = tooltip;
	}

	protected int getProgress(int length) {
		return function.apply(length);
	}

	@Override
	public void getDynamicTooltip(List<String> tooltip, int mouseX, int mouseY) {
		if (this.tooltip != null) this.tooltip.accept(tooltip);
	}
}
