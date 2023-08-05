package roj.text.logging;

/**
 * @author Roj233
 * @since 2022/6/1 5:26
 */
public enum Level {
	ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF;

	public boolean canLog(Level otherLevel) { return otherLevel.ordinal() >= ordinal(); }
	public boolean isInputLevel() { return this != ALL && this != OFF; }
}
