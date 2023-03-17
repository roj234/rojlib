package ilib.util;

import java.awt.*;

public enum EnumIO {
	DEFAULT(0), INPUT(0x960066FF), OUTPUT(0x96FF6600), //OUTPUT_A(0), OUTPUT_B(0), OUTPUT_C(0),
	ALL(0x96009600), DISABLED(0x96b4b4b4);

	public static final EnumIO[] VALUES = values();

	private final Color color;

	EnumIO(int c) {
		color = new Color(c, true);
	}

	public boolean canOutput() {
		return this == OUTPUT || this == ALL;
	}

	public boolean canInput() {
		return this == INPUT || this == ALL;
	}

	public static EnumIO byId(int id) {
		return byId(id, DEFAULT);
	}

	public static EnumIO byId(int id, EnumIO def) {
		if (id > -1 && id < VALUES.length) {
			return VALUES[id];
		}
		return def;
	}

	public Color getColor() {
		return color;
	}
}
