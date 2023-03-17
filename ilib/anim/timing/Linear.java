package ilib.anim.timing;

/**
 * @author Roj234
 * @since 2021/5/27 22:20
 */
public class Linear extends Simple {
	@Override
	public double interpolate(double percent) {
		return percent;
	}

	@Override
	public String name() {
		return "ilib:linear";
	}
}
