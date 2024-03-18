package roj.asmx.classpak;

import roj.asmx.classpak.loader.CPMain;

import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/3/18 0018 7:38
 */
public class FakeMain {
	public static void main(String[] args) {}
	public static final class Loader implements Consumer<Object> {
		public static void init() { CPMain.callback = new Loader(); }
		public void accept(Object o) { FakeMain.main((String[]) o); }
	}
}