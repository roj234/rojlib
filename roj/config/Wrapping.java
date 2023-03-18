package roj.config;

import roj.config.data.CEntry;
import roj.config.serial.SerializerManager;

@Deprecated
public class Wrapping {
	public static final SerializerManager DEFAULT = new SerializerManager();
	public static CEntry wrap(Object o) {
		return DEFAULT.serialize(o);
	}
}
