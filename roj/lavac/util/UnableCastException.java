package roj.lavac.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/31 18:44
 */
public class UnableCastException extends Exception {
	public final String from, to;

	public UnableCastException(String from, String to) {
		super("Should be caught", null, true, false);
		this.from = from;
		this.to = to;
	}
}
