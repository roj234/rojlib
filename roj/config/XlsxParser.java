package roj.config;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.data.XElement;
import roj.config.data.XEntry;
import roj.config.data.XHeader;
import roj.config.word.ITokenizer;
import roj.io.FastFailException;
import roj.text.StreamReader;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj234
 * @since 2022/12/10 0010 12:02
 */
public abstract class XlsxParser extends XMLParser {
	// 奇技淫巧（狗头
	private final XElement fakeElement = new XElement("") {
		@Override
		public void add(@Nonnull XEntry entry) {
			consumer.accept(entry);
		}
	};
	private final Map<String, XElement> replaceNodes = new MyHashMap<>();
	//private final List<XElement> cellNodes = new SimpleList<>();
	private Consumer<XEntry> consumer;

	private final MyHashSet<CharSequence> ipool1 = new MyHashSet<>();
	private Charset cs;

	private final SimpleList<String> sharedStrings = new SimpleList<>();
	private final SimpleList<String> cells = new SimpleList<>();
	private final int[] cellPos = new int[2];

	public XlsxParser() {
		// 共享字符串表
		replaceNodes.put("sst", fakeElement);
		replaceNodes.put("s", new XElement("s"));
		replaceNodes.put("t", new XElement("t"));

		// 工作表
		replaceNodes.put("worksheet", new XElement("worksheet"));
		replaceNodes.put("sheetData", fakeElement);
		replaceNodes.put("row", new XElement("row"));

		sharedStrings.capacityType = 2;
	}

	@Override
	protected XElement createElement(String name) {
		XElement el = replaceNodes.get(name);
		if (el == null) return super.createElement(name);

		el.clear();
		el.attributesForRead().clear();
		return el;
	}

	public void parse(File file, Charset cs) throws IOException, ParseException {
		this.cs = cs;

		sharedStrings.clear();
		ipool1.clear();

		try (ZipFile zf = new ZipFile(file, ZipFile.OPEN_READ, cs)) {
			ZipEntry ze = zf.getEntry("xl/sharedStrings.xml");

			ipool = ipool1;

			readWith(zf, ze, entry -> sharedStrings.add(entry.getFirstTag("t").valueAsString()));

			ipool = null;

			int sheet = 1;
			while (true) {
				ze = zf.getEntry("xl/worksheets/sheet"+sheet+".xml");
				if (ze == null) break;

				int finalSheet = sheet;
				XElement worksheet = replaceNodes.get("worksheet");
				readWith(zf, ze, entry -> {
					XElement el = worksheet.getFirstTag("sheetPr");
					String name = el == null ? null : el.attr("codeName").asString();

					el = worksheet.getFirstTag("dimension");
					String dim = el == null ? null : el.attr("ref").asString();

					onSheetChange(finalSheet, name, dim);

					consumer = entry1 -> {
						boolean nonEmpty = false;
						cells.clear();

						List<XEntry> row = entry1.childrenForRead();
						for (int i = 0; i < row.size(); i++) {
							nonEmpty |= processCell(row.get(i));
						}

						if (nonEmpty) {
							int id = entry1.attr("r").asInteger();
							try {
								onRow(id, cells);
							} catch (Throwable e) {
								throw new FastFailException("At row " + id + " content: " + cells, e);
							}
						}
					};
					consumer.accept(entry);
				});

				sheet++;
			}
		}

		sharedStrings.clear();
	}

	private boolean processCell(XEntry cell) {
		splitPos(cell.attr("r").asString(), cellPos);
		int pos = cellPos[1];
		if (cell.size() == 0) {
			setAt(pos, "");
			return false;
		}

		String type = cell.attr("t").asString();
		String value = cell.child(0).child(0).asString();
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
			if (ITokenizer.NUMBER.contains(str.charAt(i))) break;
			i++;
		}

		int yPos = 0;
		for (int j = 0; j < i; j++) {
			int v = str.charAt(j) - ('A'-1);
			yPos *= 26;
			yPos += v;
		}

		xy[0] = TextUtil.parseInt(str, i, str.length(), 10);
		xy[1] = yPos;
	}

	private XHeader readWith(ZipFile zip, ZipEntry entry, Consumer<XEntry> c) throws IOException,ParseException {
		try (InputStream in = zip.getInputStream(entry)) {
			consumer = c;
			return parseTo1(new StreamReader(in, cs), 0);
		} finally {
			clearIPool();
		}
	}
}
