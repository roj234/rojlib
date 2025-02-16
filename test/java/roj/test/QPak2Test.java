package roj.test;

import roj.archive.qz.QZEntry;
import roj.archive.qz.QZFileWriter;
import roj.test.internal.Test;
import roj.text.DateParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static roj.archive.qpak.QIncrementPak.closeIncrementalV2;
import static roj.archive.qpak.QIncrementPak.openIncrementalV2;

/**
 * @author Roj234
 * @since 2024/5/23 0023 0:41
 */
@Test("测试7z增量压缩包模式2")
public class QPak2Test {
	public static void main(String[] args) throws IOException {
		for (int i = 0; i < 3; i++) {
			QZFileWriter myfile = openIncrementalV2(new File("testqpv2.7z"));
			myfile.beginEntry(QZEntry.ofNoAttribute("fileTest"+i));
			for (int j = 0; j < 1000; j++) {
				myfile.write(("testTime="+DateParser.toLocalTimeString(System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8));
			}
			closeIncrementalV2(myfile);
		}
	}
}