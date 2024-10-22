package roj.reflect.litasm;

import roj.reflect.Bypass;

import java.io.File;

/**
 * @author Roj234
 * @since 2024/10/25 0025 4:56
 */
public interface LibraryLoader {
	LibraryLoader INSTANCE = getInstance();

	private static LibraryLoader getInstance() {
		try {
			// !! didn't know its Java8 name !!
			return Bypass.builder(LibraryLoader.class).delegate(ClassLoader.class, new String[]{"loadLibrary","loadLibrary"}, new String[]{"loadLibrary","loadLibraryEx"}).delegate_o(Class.forName("jdk.internal.loader.NativeLibrary"), "find").build();
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	Object loadLibrary(Class<?> fromClass, String name);
	Object loadLibraryEx(Class<?> fromClass, File name);
	long find(Object library, String name);
}
