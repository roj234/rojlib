package roj;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.compiler.runtime.RtUtil;
import roj.config.ConfigMaster;
import roj.config.Tokenizer;
import roj.config.auto.SerializerFactory;
import roj.gui.GuiUtil;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.text.TextReader;
import roj.ui.Terminal;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 准备删掉的东西
 */
public class Unused {
	/**
	 * 深度转换为字符串
	 * @deprecated 这个方案比之前的好，但是依然做不到contextual
	 */
	@Deprecated
	public static String deepToString(Object o) {return ConfigMaster.YAML.writeObject(SerializerFactory.UNSAFE.serializer(o.getClass()), Helpers.cast(o), new CharList()).toStringAndFree();}

	public static void pack(int[] arr) {
		var sb = RtUtil.pack(arr);
		GuiUtil.setClipboardText(Tokenizer.escape(new CharList().append('"'), sb, 0, '\'').append('"').toStringAndFree());
		Terminal.pause();
	}
	public static void pack(byte[] arr) {
		var sb = RtUtil.pack(arr);
		GuiUtil.setClipboardText(Tokenizer.escape(new CharList().append('"'), sb, 0, '\'').append('"').toStringAndFree());
		Terminal.pause();
	}

	public static void runOnce() throws Exception {
		Class<?> caller = Reflection.getCallerClass(2);
		ZipFile za = new ZipFile(IOUtil.getJar(caller));
		ZEntry entry = za.getEntry(caller.getName().replace('.', '/') + ".class");
		int crc32 = entry.getCrc32();
		String expect = entry.getName() + ":" + crc32;

		File mainFile = new File(".code_runner");
		if (mainFile.isFile()) {
			try (var tr = TextReader.auto(mainFile)) {
				for (String line : tr.lines()) {
					if (line.equals(expect)) {
						throw new IllegalStateException("相同的代码已经被执行过了");
					}
				}
			}
		}
		try (var fos = new FileOutputStream(mainFile, true)) {
			fos.write((expect+"\n").getBytes(StandardCharsets.UTF_8));
		}
		System.out.println("Execute only once: "+expect);
	}
}