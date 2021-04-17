package roj.text.logging;

/**
 * @author Roj233
 * @since 2022/6/1 5:26
 */
public enum Level {
	TRACE, DEBUG, INFO, WARN, ERROR, SEVERE, CRITICAL, NONE;

	public boolean canLog(Level otherLevel) {
		return otherLevel.ordinal() >= ordinal();
	}
}
