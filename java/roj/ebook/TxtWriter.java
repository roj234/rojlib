package roj.ebook;

import roj.io.IOUtil;
import roj.io.source.Source;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/07/11 07:22
 */
public class TxtWriter extends EbookWriter {
	public static Metadata<Charset> CHARSET = new Metadata<>("txt:charset", StandardCharsets.UTF_8);

	private static final String HEADER =
			"==========================================================\n" +
			"更多小说尽在百度搜索：https://www.baidu.com/\n" +
			"==========================================================";

	private final TextWriter writer;

	public TxtWriter(Map<Metadata<?>, ?> metadataList, Source output) throws IOException {
		writer = new TextWriter(output, getMetadata(metadataList, CHARSET));

		writer.append(HEADER).append("\n\n");

		String title = getMetadata(metadataList, TITLE);
		String author = getMetadata(metadataList, AUTHOR);

		writer.append(title).append(" 作者：").append(author).append("\n\n");

		String s;

		s = getMetadata(metadataList, DESCRIPTION);
		if (s != null) {
			writer.append("内容简介：\n");
			formatWrite(s);
			writer.append("\n\n");
		}

		s = getMetadata(metadataList, SUBJECT);
		if (s != null) writer.append("类型：").append(s).append("\n");

		for (Map.Entry<Metadata<?>, ?> entry : metadataList.entrySet()) {
			if (entry.getValue() instanceof String value) {
				writer.append(entry.getKey()).append("：").append(value).append("\n");
			}
		}
	}

	private void formatWrite(CharSequence seq) {
		LineReader.Impl lr = LineReader.create(seq);
		CharList sb = IOUtil.getSharedCharBuf();
		while (lr.readLine(sb)) {
			if (sb.rtrim().length() > 0) writer.append("\t").append(sb);
			writer.append("\n");
			sb.clear();
		}

	}

	@Override
	public void addLeafChapter(Chapter chapter, Function<Chapter, CharList> encoder) throws IOException {
		writer.append("\n\n");
		if (chapter.hasCustomName) {
			writer.append(chapter.customName).append('\n');
		} else {
			writer.append("第").append(chapter.index).append(chapter.type).append(' ').append(chapter.name).append('\n');
		}
		formatWrite(encoder != null ? encoder.apply(chapter) : chapter.text);
		writer.append('\n');
	}

	@Override public void finish() throws IOException {writer.append(HEADER);writer.finish();}
	@Override public void close() throws IOException {writer.close();}
}
