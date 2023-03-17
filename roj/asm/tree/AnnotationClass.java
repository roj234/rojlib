package roj.asm.tree;

public interface AnnotationClass {
	String repeatOn();

	int TYPE = 1 << 2, FIELD = 1 << 3, METHOD = 1 << 4, PARAMETER = 1 << 5, CONSTRUCTOR = 1 << 6, LOCAL_VARIABLE = 1 << 7, ANNOTATION_TYPE = 1 << 8, PACKAGE = 1 << 9, // 1.8
	TYPE_PARAMETER = 1 << 10, TYPE_USE = 1 << 11;

	// bit set
	int applicableTo();

	int SOURCE = 1, CLASS = 0, RUNTIME = 2;

	int kind();

	class Modifiable implements AnnotationClass {
		public String repeatOn = null;
		public int applicableTo = -1;
		public byte kind = 0;

		@Override
		public String repeatOn() {
			return repeatOn;
		}

		@Override
		public int applicableTo() {
			return applicableTo;
		}

		@Override
		public int kind() {
			return kind;
		}
	}
}
