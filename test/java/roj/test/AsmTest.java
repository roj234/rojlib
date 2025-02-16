package roj.test;

import roj.asm.util.Context;
import roj.io.IOUtil;
import roj.test.internal.Test;

import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/5/23 0023 0:41
 */
@Test("测试RojASM")
public class AsmTest {
	public static void main(String[] args) throws IOException {
		List<Context> ctxList = Context.fromZip(IOUtil.getJar(Context.class), null);
		for (int i = 0; i < 2; i++) {
			for (Context ctx : ctxList) {
				ctx.getData();
				ctx.getCompressedShared();
				ctx.getData();
				ctx.getCompressedShared();
			}
		}
	}
}