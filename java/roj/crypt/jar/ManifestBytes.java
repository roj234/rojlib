package roj.crypt.jar;

import roj.collect.HashMap;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/3/25 16:57
 */
final class ManifestBytes {
	byte[] data;
	Map<String, NamedAttr> namedAttrMap = new HashMap<>();
	ByteAttr mainAttr;

	public ManifestBytes(InputStream in) throws IOException {this(IOUtil.read(in));}
	@SuppressWarnings("fallthrough")
	public ManifestBytes(byte[] data) {
		this.data = data;
		List<ByteAttr> tmp = new ArrayList<>();

		int i = 0, len = data.length;
		loop:
		while (true) {
			int last = i-1;
			boolean isEmptyLine = true;

			int lineEnd = -2;

			while (i < len) {
				byte b = data[i];
				switch (b) {
					case '\r':
						if (lineEnd == -2) lineEnd = i-1;
						if (i < len-1 && data[i+1] == '\n') i++;

					case '\n':
						if (lineEnd == -2) lineEnd = i-1;

						if (isEmptyLine || (i == len-1)) {
							tmp.add(new ByteAttr(lineEnd, isEmptyLine ? last : i, ++i));
							continue loop;
						}

						// start of a new line
						last = i;
						isEmptyLine = true;
					break;
					default:
						isEmptyLine = false;
					break;
				}
				i++;
			}

			break;
		}

		Map<String, NamedAttr> map = namedAttrMap;
		Function<String, NamedAttr> mapper = NamedAttr::new;
		for (int j = 1; j < tmp.size(); j++) {
			ByteAttr attr = tmp.get(j);
			String name = attr.init(data, tmp.get(j-1));
			map.computeIfAbsent(name, mapper).sections.add(attr);
		}
		mainAttr = tmp.get(0);
	}

	public byte[] digest(MessageDigest digest, String s) {
		NamedAttr attr = namedAttrMap.get(s);
		if (attr == null && s == null) {
			digest.reset();
			digest.update(data, mainAttr.offset, mainAttr.startOfNext);
			return digest.digest();
		}
		return attr.digest(digest, data);
	}

	static final class NamedAttr {
		final String name;
		final List<ByteAttr> sections = new ArrayList<>();
		NamedAttr(String name) { this.name = name; }

		byte[] digest(MessageDigest digest, byte[] data) {
			digest.reset();
			for (ByteAttr section : sections) {
				digest.update(data, section.offset, section.startOfNext-section.offset);
			}

			return digest.digest();
		}
	}
	static final class ByteAttr {
		int offset;
		final int endOfLastLine, endOfSection, startOfNext;

		ByteAttr(int endOfLastLine, int endOfSection, int startOfNext) {
			this.endOfLastLine = endOfLastLine;
			this.endOfSection = endOfSection;
			this.startOfNext = startOfNext;
		}

		String getAllLines(byte[] data) {
			int myTerminate = endOfLastLine;
			while (true) {
				myTerminate++;
				if (data[myTerminate] == '\n' && (myTerminate+1 == data.length || data[myTerminate+1] != ' ')) break;
			}
			return new String(data, myTerminate, endOfSection - myTerminate - 1);
		}

		void getAllLines(byte[] data, Map<String, String> map) {
			for (String line : LineReader.create(getAllLines(data))) {
				int pos = line.indexOf(": ");
				map.put(line.substring(0, pos), line.substring(pos+2));
			}
		}

		@SuppressWarnings("fallthrough")
		String init(byte[] data, ByteAttr p) {
			int i = offset = p.startOfNext;
			if (data[i++] != 'N' || data[i++] != 'a' || data[i++] != 'm' || data[i++] != 'e' || data[i++] != ':' || data[i++] != ' ') {
				throw new IllegalStateException("Attribute"+this+" missing name");
			}

			int prev = i;

			ByteList buf = IOUtil.getSharedByteBuf();
			loop:
			while (true) {
				byte c = data[i];
				switch (c) {
					case '\r':
						buf.put(data, prev, i-prev);

						if (i+1 < data.length && data[i+1] == '\n')
							i++;

						prev = i;
					case '\n':
						buf.put(data, prev, i-prev);

						if (i+1 >= data.length || data[i+1] != ' ') break loop;

						// line wrap
						i++;
						prev = i+1;
				}

				if (++i >= data.length) break;
			}
			buf.put(data, prev, i-prev);

			return buf.readUTF(buf.readableBytes());
		}
	}
}