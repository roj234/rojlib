package roj.datagen;

import roj.io.IOUtil;

import java.io.File;
import java.io.FileOutputStream;

/**
 * @author Roj234
 * @since 2025/09/02 16:39
 */
public class Main {
	public static void main(String[] args) throws Exception {
		var input = new File("../RojLib/datagen/data");
		var output = new File("../RojLib/core/resources");
		new MakeReflectionProxy().run(input, output);

		String path = "roj/reflect/litasm/internal/JVMCI.class";
		var data = IOUtil.getResource(path);
		try (var fos = new FileOutputStream(new File(output, path))) {
			fos.write(data);
		}

		new MakePinyinData().run(input, output);
	}
}
