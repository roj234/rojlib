package roj.platform;

import roj.io.IOUtil;
import roj.text.TextReader;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/12/25 0025 16:13
 */
public class TestPlugin extends Plugin {
	public static String awa;

	@Override
	public void onLoad() {
		System.out.println("TestPlugin onload");
	}

	@Override
	public void onEnable() {
		System.out.println("TestPlugin onEnable");
		try {
			awa = IOUtil.read(TextReader.auto(getResource("data.txt")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDisable() {
		System.out.println("TestPlugin ondisable");
	}
}
