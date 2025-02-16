package roj.text.logging;

import roj.ui.Terminal;

/**
 * @author Roj233
 * @since 2022/6/1 5:26
 */
public enum Level {
	ALL, TRACE(Terminal.YELLOW), DEBUG(Terminal.CYAN), INFO(Terminal.WHITE+Terminal.HIGHLIGHT), WARN(Terminal.YELLOW+Terminal.HIGHLIGHT), ERROR(Terminal.RED+Terminal.HIGHLIGHT), FATAL(Terminal.RED), OFF;

	public final int color;
	public final String shortName = String.valueOf(name().charAt(0));

	Level() { this(0); }
	Level(int color) { this.color = color; }

	public boolean canLog(Level otherLevel) { return otherLevel.ordinal() >= ordinal(); }
	public boolean isInputLevel() { return this != ALL && this != OFF; }

	@Override
	public String toString() {return shortName;}
}