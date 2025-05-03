package roj.gui;

/**
 * @author Roj234
 * @since 2023/11/28 2:15
 */
public class CMBoxValue {
	public final String name;
	public final int value;

	public CMBoxValue(String name, int value) {
		this.name = name;
		this.value = value;
	}

	@Override
	public String toString() { return name; }
}
