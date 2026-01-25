package roj.staging.new_omc;

/**
 * @author Roj234
 * @since 2026/02/06 16:08
 */
public final class ValueContainer {
	public boolean ZValue;
	public byte BValue;
	public char CValue;
	public short SValue;
	public int IValue;
	public long JValue;
	public float FValue;
	public double DValue;
	public Object LValue;

	@Override
	public String toString() {
		return "ValueContainer{" +
					   "Z=" + ZValue +
					   ", B=" + BValue +
					   ", C=" + CValue +
					   ", S=" + SValue +
					   ", I=" + IValue +
					   ", J=" + JValue +
					   ", F=" + FValue +
					   ", D=" + DValue +
					   ", L=" + LValue +
					   '}';
	}
}
