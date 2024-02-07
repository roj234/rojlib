package roj.excel;

import org.jetbrains.annotations.NotNull;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.data.CEntry;
import roj.config.data.Element;
import roj.config.data.Node;
import roj.config.serial.ToXEntry;
import roj.config.word.Tokenizer;
import roj.io.FastFailException;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/12/10 0010 12:02
 */
public abstract class XlsxReader {
	private static final class Dummy extends Element {
		public Dummy() { super(""); }

		public void attr(String k, CEntry v) {}
		public void add(@NotNull Node node) {}
	}
	private static final Element FAKE = new Dummy();

	// 奇技淫巧（狗头
	private final XMLParser xml = new XMLParser();
	private final Map<String, Element> replaceNodes = new MyHashMap<>();
	private Consumer<Element> consumer;

	// row-col
	private final SimpleList<String> sharedStrings = SimpleList.withCapacityType(64, 2);
	private final SimpleList<String> cells = new SimpleList<>();
	private final int[] cellPos = new int[2];
	private boolean emptyRow;

	public XlsxReader() {
		// 共享字符串表
		replaceNodes.put("sst", new Element("sst"));
		replaceNodes.put("si", FAKE);
		replaceNodes.put("t", new Element("t"));

		// 工作表
		replaceNodes.put("worksheet", new Element("worksheet"));
		replaceNodes.put("sheetData", FAKE);
		replaceNodes.put("row", FAKE);
		replaceNodes.put("c", new Element("c"));
		replaceNodes.put("v", new Element("v"));
	}

	public void parse(File file, Charset charset) throws IOException, ParseException {
		parse(new FileSource(file), charset);
	}

	public void parse(Source file, Charset charset) throws IOException, ParseException {
		xml.charset = charset;

		try (ZipArchive zf = new ZipArchive(file, 0, charset)) {
			ZEntry ze = zf.getEntries().get("xl/sharedStrings.xml");

			readWith(zf, ze, entry -> {
				if (sharedStrings.isEmpty()) sharedStrings.ensureCapacity(replaceNodes.get("sst").attr("count").asInteger());
				sharedStrings.add(entry.textContent());
			});

			Consumer<Element> rowHandler = xe -> {
				if (xe.tag.equals("c")) {
					if (processCell(xe)) emptyRow = false;
				} else {
					if (!emptyRow) {
						int id = xe.attr("r").asInteger();
						try {
							onRow(id, cells);
						} catch (Throwable e) {
							throw new FastFailException("At row " + id + " content: " + cells, e);
						}
					}

					cells.clear();
					emptyRow = true;
				}
			};

			int sheet = 1;
			while (true) {
				ze = zf.getEntries().get("xl/worksheets/sheet"+sheet+".xml");
				if (ze == null) break;

				cells.clear();
				emptyRow = true;

				int finalSheet = sheet;
				readWith(zf, ze, entry -> {
					rowHandler.accept(entry);
					if (entry.tag.equals("c")) return;

					Element worksheet = replaceNodes.get("worksheet");

					Element el = worksheet.getElementByTagName("sheetPr");
					String name = el == null ? null : el.attr("codeName").asString();

					el = worksheet.getElementByTagName("dimension");
					String dim = el == null ? null : el.attr("ref").asString();

					onSheetChange(finalSheet, name, dim);

					consumer = rowHandler;
				});

				sheet++;
			}
		} finally {
			sharedStrings.clear();
		}
	}

	private boolean processCell(Node cell) {
		splitPos(cell.attr("r").asString(), cellPos);
		int pos = cellPos[1];
		if (cell.size() == 0) {
			setAt(pos, "");
			return false;
		}

		String type = cell.attr("t").asString();
		String value = cell.child(0).child(0).textContent();
		switch (type) {
			// string
			case "s": value = sharedStrings.get(Integer.parseInt(value)); break;
			// number
			case "": break;
			// error
			case "e": break;
		}
		setAt(pos, value);
		return true;
	}

	private void setAt(int pos, String s) {
		cells.ensureCapacity(pos);
		while (cells.size() < pos) cells.add("");
		cells.set(pos-1, s);
	}

	protected void onSheetChange(int sheetId, String sheetName, String sheetDimension) {}
	protected abstract void onRow(int rowNumber, List<String> value);

	public static void splitPos(CharSequence str, int[] xy) {
		int i = 1;
		while (i < str.length()) {
			if (Tokenizer.NUMBER.contains(str.charAt(i))) break;
			i++;
		}

		int yPos = 0;
		for (int j = 0; j < i; j++) {
			int v = str.charAt(j) - ('A'-1);
			yPos *= 26;
			yPos += v;
		}

		xy[0] = TextUtil.parseInt(str, i, str.length());
		xy[1] = yPos;
	}

	private void readWith(ZipArchive zip, ZEntry entry, Consumer<Element> c) throws IOException,ParseException {
		consumer = c;

		try (InputStream in = zip.getInput(entry)) {
			xml.parseRaw(new ToXEntry() {
				@Override
				protected Element createElement(String str) {
					Element el = replaceNodes.get(str);
					if (el == null) {
						System.out.println("non-recyclable children " + str);
						return new Element(str);
					}

					el.clear();
					el.attributesForRead().clear();
					return el;
				}

				@Override
				protected void beforePop(Element child, Element parent) {
					if (parent == FAKE) consumer.accept(child);
				}
			}, in, 0);
		}
	}
}