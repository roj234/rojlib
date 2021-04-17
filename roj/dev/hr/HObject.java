package roj.dev.hr;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:36
 */
public final class HObject {
	public final HKlass klass;
	public final HField[] fields;

	public HObject(HKlass klass, HField[] fields) {
		this.klass = klass;
		this.fields = fields;
	}
}
