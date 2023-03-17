package roj.config;

import roj.config.data.CEntry;
import roj.config.serial.SerializerFactory;
import roj.config.serial.SerializerFactoryFactory;

@Deprecated
public class Wrapping {
	public static final SerializerFactory DEFAULT = SerializerFactoryFactory.create();
	public static CEntry wrap(Object o) {
		return DEFAULT.serialize(o);
	}
}
