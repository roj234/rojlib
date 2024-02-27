package roj.asmx.event;

/**
 * @author Roj234
 * @since 2024/3/21 0021 13:19
 */
public enum Priority {
	HIGHEST, HIGH, NORMAL, LOW, LOWEST;

	public static final byte MASK = 7;
	public static final int PRIORITY_COUNT = 5;
	static final Priority[] PRIORITIES = values();
}