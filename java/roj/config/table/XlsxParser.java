package roj.config.table;

import org.jetbrains.annotations.NotNull;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.XMLParser;
import roj.config.data.CEntry;
import roj.config.data.Element;
import roj.config.data.Node;
import roj.config.serial.ToXml;
import roj.io.FastFailException;
import roj.io.source.Source;
import roj.text.TextUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/12/10 0010 12:02
 */
final class XlsxParser implements TableParser {
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
	private final SimpleList<String> sharedStrings = SimpleList.hugeCapacity(64);
	private final SimpleList<String> cells = new SimpleList<>();
	private final int[] cellPos = new int[2];
	private boolean emptyRow;

	public XlsxParser() {
		// 共享字符串表
		replaceNodes.put("sst", new Element("sst"));
		replaceNodes.put("si", FAKE);
		replaceNodes.put("t", new Element("t"));

		// 工作表列表 Rels
		replaceNodes.put("Relationships", FAKE);
		replaceNodes.put("Relationship", new Element("Relationship"));

		// 工作表列表
		replaceNodes.put("sheets", FAKE);
		replaceNodes.put("sheet", new Element("sheet"));

		// 工作表
		replaceNodes.put("worksheet", new Element("worksheet"));
		replaceNodes.put("sheetData", FAKE);
		replaceNodes.put("row", FAKE);
		replaceNodes.put("c", new Element("c"));
		replaceNodes.put("v", new Element("v"));

		replaceNodes.put("col", new Element("col"));
		replaceNodes.put("mergeCell", new Element("mergeCell"));
	}

	public void table(Source file, Charset charset, TableReader listener) throws IOException, ParseException {
		if (charset == null) charset = StandardCharsets.UTF_8;
		xml.charset = charset;

		try (ZipFile zf = new ZipFile(file, ZipFile.FLAG_BACKWARD_READ, charset)) {
			zf.reload();
			ZEntry ze = zf.getEntry("xl/sharedStrings.xml");

			readWith(zf, ze, entry -> {
				if (sharedStrings.isEmpty()) sharedStrings.ensureCapacity(replaceNodes.get("sst").attr("count").asInt());
				sharedStrings.add(entry.textContent());
			});

			Consumer<Element> rowHandler = xe -> {
				if (xe.tag.equals("c")) {
					if (processCell(xe)) emptyRow = false;
				} else {
					if (!emptyRow) {
						// row是FAKE，懒得取了
						int id = cellPos[0];
						try {
							listener.onRow(id, cells);
						} catch (Throwable e) {
							throw new FastFailException("At row "+id+" content: "+cells, e);
						}
					}

					cells.clear();
					emptyRow = true;
				}
			};

			ze = zf.getEntry("xl/_rels/workbook.xml.rels");
			var relMap = new MyHashMap<String, String>();
			readWith(zf, ze, entry -> {
				relMap.put(entry.attr("Id").asString(), entry.attr("Target").asString());
			});

			ze = zf.getEntry("xl/workbook.xml");
			var sheetList = new SimpleList<>();
			readWith(zf, ze, entry -> {
				int id = entry.attr("sheetId").asInt();
				String name = entry.attr("name").asString();
				if (listener.readSheet(id, name)) {
					sheetList.add(name);
					sheetList.add(id);
					var relInfo = zf.getEntry("xl/"+relMap.get(entry.attr("r:id").asString()));
					if (relInfo == null) throw new IllegalStateException("找不到RelationId:"+entry);
					sheetList.add(relInfo);
				}
			});

			for (int i = 0; i < sheetList.size(); i += 3) {
				cells.clear();
				emptyRow = true;

				int javac傻逼 = i;
				readWith(zf, (ZEntry) sheetList.get(i+2), entry -> {
					rowHandler.accept(entry);
					if (entry.tag.equals("c")) return;

					var el = replaceNodes.get("worksheet").getElementByTagName("dimension");
					String dim = el == null ? null : el.attr("ref").asString();

					String name = (String) sheetList.get(javac傻逼);
					listener.onSheet((Integer) sheetList.get(javac傻逼+1), name, dim);

					consumer = rowHandler;
				});
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

	private void readWith(ZipFile zip, ZEntry entry, Consumer<Element> c) throws IOException,ParseException {
		consumer = c;

		try (InputStream in = zip.getStream(entry)) {
			xml.parse(in, 0, new ToXml() {
				@Override
				protected Element createElement(String str) {
					Element el = replaceNodes.get(str);
					if (el == null) {
						//System.out.println("non-recyclable children " + str);
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
			});
		}
	}
}