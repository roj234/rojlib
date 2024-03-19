package roj.text.logging;

import roj.ui.CLIUtil;

/**
 * @author Roj233
 * @since 2022/6/1 5:26
 */
public enum Level {
	ALL, TRACE(CLIUtil.YELLOW), DEBUG(CLIUtil.CYAN), INFO(CLIUtil.WHITE+CLIUtil.HIGHLIGHT), WARN(CLIUtil.YELLOW+CLIUtil.HIGHLIGHT), ERROR(CLIUtil.RED+CLIUtil.HIGHLIGHT), FATAL(CLIUtil.RED), OFF;

	public final int color;
	public final String shortName = String.valueOf(name().charAt(0));

	Level() { this(0); }
	Level(int color) { this.color = color; }

	public boolean canLog(Level otherLevel) { return otherLevel.ordinal() >= ordinal(); }
	public boolean isInputLevel() { return this != ALL && this != OFF; }

	@Override
	public String toString() {return shortName;}
}