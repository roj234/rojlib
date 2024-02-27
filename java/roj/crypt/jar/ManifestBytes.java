package roj.crypt.jar;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/25 0025 16:57
 */
public class ManifestBytes {
	byte[] data;
	Map<String, NamedAttr> namedAttrMap = new MyHashMap<>();
	ByteAttr mainAttr;

	public ManifestBytes(InputStream in) throws IOException {this(IOUtil.read(in));}
	@SuppressWarnings("fallthrough")
	public ManifestBytes(byte[] data) {
		this.data = data;
		List<ByteAttr> tmp = new SimpleList<>();

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
		for (int j = 1; j < tmp.size(); j++) {
			ByteAttr attr = tmp.get(j);
			String name = attr.init(data, tmp.get(j-1));
			map.computeIfAbsent(name, NamedAttr::new).sections.add(attr);
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
		final List<ByteAttr> sections = new SimpleList<>();
		NamedAttr(String name) { this.name = name; }

		byte[] digest(MessageDigest digest, byte[] data) {
			digest.reset();
			for (ByteAttr section : sections) {
				digest.update(data, section.offset, section.startOfNext-section.offset);
			}

			return digest.digest();
		}
	}
	private static final class ByteAttr {
		int offset;
		final int endOfLastLine, endOfSection, startOfNext;

		ByteAttr(int endOfLastLine, int endOfSection, int startOfNext) {
			this.endOfLastLine = endOfLastLine;
			this.endOfSection = endOfSection;
			this.startOfNext = startOfNext;
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