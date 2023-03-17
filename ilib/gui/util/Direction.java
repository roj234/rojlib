package ilib.gui.util;

/**
 * Tells the component which way to render the texture
 */
public enum Direction {
	UP, DOWN, LEFT, RIGHT;

	public boolean isHorizontal() {
		return ordinal() > 1;
	}

	public boolean isVertical() {
		return ordinal() <= 1;
	}

	public int getAngle() {
		switch (this) {
			default:
				return 0;
			case DOWN:
				return 180;
			case LEFT:
				return -90;
			case RIGHT:
				return 90;
		}
	}
}
