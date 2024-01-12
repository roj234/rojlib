package roj.excel;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.collect.IntList;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.io.source.MemorySource;
import roj.io.source.Source;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/5/5 0005 15:16
 */
public class XlsxWriter implements TableWriter {
	private static ZipArchive TEMPLATE;
	static {
		try {
			TEMPLATE = new ZipArchive(new MemorySource(IOUtil.getResource("META-INF/template.xlsx")), 0, StandardCharsets.UTF_8);
			TEMPLATE.reload();
		} catch (IOException ignored) {}
	}

	private final ZipFileWriter zip;

	private String sheetName;
	private ByteList sheet;
	private IntList cw;
	private final List<Object> sheets = new SimpleList<>();

	public static XlsxWriter to(Source out) throws IOException {
		return new XlsxWriter(new ZipFileWriter(out, 5, false, 0));
	}
	public static XlsxWriter to(File out) throws IOException {
		return new XlsxWriter(new ZipFileWriter(out));
	}

	public XlsxWriter(ZipFileWriter zfw) throws IOException {
		zip = zfw;
		for (ZEntry entry : TEMPLATE.getEntries().values()) {
			if (!entry.getName().endsWith("/")) zfw.copy(TEMPLATE, entry);
		}
		zip.beginEntry(new ZEntry("xl/sharedStrings.xml"));
		tmpB.putAscii("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">").writeToStream(zip);
		tmpB.clear();
	}

	private final ToIntMap<String> sharedStrings = new ToIntMap<>();
	private final ByteList tmpB = new ByteList();
	private int sst(String key) throws IOException {
		int id = sharedStrings.getOrDefault(key, -1);
		if (id < 0) {
			sharedStrings.putInt(key, id = sharedStrings.size());

			tmpB.clear();
			tmpB.putAscii("<si><t>").putUTFData(xmlEncode(key)).putAscii("</t></si>").writeToStream(zip);
		}
		return id;
	}

	private final CharList tmpC2 = new CharList();
	private CharList xmlEncode(String key) {
		tmpC2.clear();
		return tmpC2.append(key).replace("&", "&amp;").replace("<", "&lt;");
	}

	private final CharList tmpC = new CharList();
	private CharList rc(int col) {
		int i = tmpC.length();

		do {
			tmpC.append((char) ('A' + (col % 26)));
			col /= 26;
		} while (col != 0);

		char[] arr = tmpC.list;
		int length = tmpC.length();
		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			char a = arr[i];
			arr[i] = arr[length - i];
			arr[length - i] = a;
		}

		return tmpC;
	}

	public void beginSheet(String name) {
		if (sheet != null) {
			sheets.add(sheetName);
			sheets.add(sheet);
			sheets.add(cw);
		}

		maxHeight = 0;
		if (name != null) {
			sheetName = name;
			sheet = new ByteList();
		} else {
			sheet = null;
		}
		cw = null;
	}

	@Override
	public void setColWidth(int col, float width) {
		if (cw == null) cw = new IntList();
		cw.setSize(Math.max(cw.size(), col+1));
		cw.getRawArray()[col] = Float.floatToRawIntBits(width);
	}

	private int maxHeight;
	@Override
	public void writeRow(List<?> row) throws IOException {
		int r = ++maxHeight;

		// <row r="1" spans="1:1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
		int min = 0, max = row.size();
		for (int i = 0; i < max; i++) {
			Object o = row.get(i);
			if (o != null && !"".equals(o)) {
				min = i; break;
			}
		}
		for (int i = max - 1; i >= min; i--) {
			Object o = row.get(i);
			if (o != null && !"".equals(o)) {
				max = i; break;
			}
		}

		tmpC.append("<row r=\"").append(r).append("\" spans=\"").append(min+1).append(":").append(max+1).append("\">");
		for (int i = 0; i < row.size(); i++) {
			tmpC.append("<c r=\"");
			rc(i).append(r);

			Object key = row.get(i);
			if (key instanceof Number) {
				tmpC.append("\"><v>").append(key.toString());
			} else {
				tmpC.append("\" t=\"s\"><v>").append(sst(key == null ? "" : key.toString()));
			}
			tmpC.append("</v></c>");
		}
		sheet.putUTFData(tmpC.append("</row>"));
		tmpC.clear();
	}

	@Override
	public void close() throws IOException {
		tmpB.clear();
		tmpB.putAscii("</sst>").writeToStream(zip);

		zip.closeEntry();
		beginSheet(null);
		for (int i = 0; i < sheets.size(); i += 3) {
			zip.beginEntry(new ZEntry("xl/worksheets/sheet"+(i/3+1)+".xml"));

			tmpB.clear();
			tmpB.putAscii("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
				"<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:x14=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/main\" xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\">");

			IntList colWidth = (IntList) sheets.get(i+2);
			if (colWidth != null) {
				tmpB.putAscii("<cols>");
				for (int col = 0; col < colWidth.size();) {
					int width = colWidth.get(col++);
					if (width != 0) tmpB.putAscii("<col min=\""+col+"\" max=\""+col+"\" width=\""+Float.intBitsToFloat(width)+"\" customWidth=\"1\"/>");
				}
				tmpB.putAscii("</cols>");
			}
			// <cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="91.75" customWidth="1"/></cols>

			tmpB.putAscii("<sheetData>").writeToStream(zip);
			((ByteList)sheets.get(i+1)).writeToStream(zip);

			tmpB.clear();
			tmpB.putAscii("</sheetData></worksheet>").writeToStream(zip);

			zip.closeEntry();
		}
		zip.beginEntry(new ZEntry("xl/workbook.xml"));
		tmpB.clear();
		tmpB.putAscii("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><fileVersion appName=\"xl\" lastEdited=\"3\" lowestEdited=\"5\" rupBuild=\"9302\"/><sheets>");
		for (int i = 0; i < sheets.size(); i += 3) {
			tmpB.putAscii("<sheet name=\"").putUTFData(ITokenizer.addSlashes(sheets.get(i).toString())).putAscii("\" sheetId=\""+(i/3+1)+"\" r:id=\"rId"+(i/3+1)+"\"/>");
		}
		tmpB.putAscii("</sheets></workbook>").writeToStream(zip);
		zip.closeEntry();
		zip.beginEntry(new ZEntry("xl/_rels/workbook.xml.rels"));
		tmpB.clear();
		tmpB.putAscii("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
		for (int i = 0; i < sheets.size(); i += 3) {
			tmpB.putAscii("<Relationship Id=\"rId"+(i/3+1)+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet"+(i/3+1)+".xml\"/>");
		}
		tmpB.putAscii("<Relationship Id=\"rId"+(sheets.size()/3+1)+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>");

		tmpB.putAscii("</Relationships>").writeToStream(zip);
		zip.close();

		sharedStrings.clear();
	}
	@Override
	public void flush() {}
}