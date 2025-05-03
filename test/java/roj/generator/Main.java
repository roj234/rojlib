package roj.generator;

import java.io.File;

/**
 * @author Roj234
 * @since 2024/5/23 0:29
 */
public class Main {
	public static File resourcePath = new File("D:\\mc\\MCMake\\projects\\rojlib\\resources");

	public static void main(String[] args) throws Exception {
		GenerateReflectionHook.run();
		GenerateReflectionHook.run2();
		UpdatePinyinData.run("D:\\Desktop\\Python39\\pinyin-data-master.txt", "D:\\Desktop\\Python39\\phrase-pinyin-data-master.txt");
	}
}