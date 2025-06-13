package roj.ebook;

import org.jetbrains.annotations.Nullable;
import roj.io.Finishable;
import roj.io.source.FileSource;
import roj.text.CharList;
import roj.util.TypedKey;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/07/11 06:56
 */
public abstract class EbookWriter implements Closeable, Finishable {
	public static EbookWriter epub(Map<Metadata<?>, ?> metadataList, File file) throws IOException {return new EpubWriter(metadataList, new FileSource(file));}
	public static EbookWriter text(Map<Metadata<?>, ?> metadataList, File file) throws IOException {return new TxtWriter(metadataList, new FileSource(file));}

	/**
	 * 标题
	 */
	public static final Metadata<String> TITLE = new Metadata<>("ebook:title");
	/**
	 * 作者
	 */
	public static final Metadata<String> AUTHOR = new Metadata<>("ebook:author");
	/**
	 * 类型
	 */
	public static final Metadata<String> SUBJECT = new Metadata<>("ebook:subject", null);
	/**
	 * 描述
	 */
	public static final Metadata<String> DESCRIPTION = new Metadata<>("ebook:description", null);
	/**
	 * 封面
	 */
	public static final Metadata<File> COVER = new Metadata<>("ebook:cover", null);

	public static class Metadata<T> extends TypedKey<T> {
		public final T defaultValue;
		public final boolean optional;

		public Metadata(String name) {super(name);this.defaultValue = null;this.optional = false;}
		public Metadata(String name, T defaultValue) {super(name);this.defaultValue = defaultValue;this.optional = true;}
	}

	@SuppressWarnings("unchecked")
	protected static <T> T getMetadata(Map<Metadata<?>, ?> metadataList, Metadata<T> metadata) {
		T value = (T) metadataList.remove(metadata);
		if (value == null) value = metadata.defaultValue;
		if (value == null && !metadata.optional) throw new NullPointerException("Necessary metadata "+metadata+" is missing");
		return value;
	}

	protected int depth;
	public void addChapter(Chapter chap, @Nullable Function<Chapter, CharList> encoder) throws IOException {
		addLeafChapter(chap, encoder);
		if (chap.getChildCount() > 0) {
			depth++;
			try {
				for (int i = 0; i < chap.children.size(); i++) {
					addChapter(chap.children.get(i), encoder);
				}
			} finally {
				depth--;
			}
		}
	}
	protected abstract void addLeafChapter(Chapter chapter, Function<Chapter, CharList> chapterEncoder) throws IOException;
}
