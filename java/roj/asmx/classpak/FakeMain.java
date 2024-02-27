package roj.asmx.classpak;

import roj.asmx.classpak.loader.CPMain;

import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/3/18 0018 7:38
 */
final class FakeMain implements Consumer<Object> {
	public static void init() {CPMain.callback = new FakeMain();}
	public void accept(Object o) {Cpk.main((String[]) o);}
}