package roj.config.serial;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;

import static roj.config.serial.SerializerFactory.*;

/**
 * @author Roj234
 * @since 2023/3/24 0024 12:45
 */
public class SerializerFactoryFactory {
	static boolean injected;
	static {
		try {
			ConstantData c = Parser.parseConstants(IOUtil.readRes("roj/config/serial/Adapter.class"));
			c.parent(DirectAccessor.MAGIC_ACCESSOR_CLASS);
			ClassDefiner.INSTANCE.defineClassC(c);
			injected = true;
		} catch (Throwable ignored) {}
	}

	public static SerializerFactory create() { return create(GENERATE|CHECK_INTERFACE|CHECK_PARENT); }
	public static SerializerFactory create(int flag) { return new SerializerFactory(flag); }
}
