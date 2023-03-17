package roj.kscript.type;

/**
 * @author Roj234
 * @since 2021/5/29 1:28
 */
public abstract class KBase implements KType {
	protected KBase() {}

	@Override
	public final String toString() {
		return toString0(new StringBuilder(), 0).toString();
	}
}
