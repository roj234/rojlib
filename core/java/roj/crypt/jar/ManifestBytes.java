package roj.crypt.jar;

import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/25 16:57
 */
final class ManifestBytes {
	final byte[] data;
	final Entry mainAttribute;
	final Map<String, List<Entry>> namedAttributes = new HashMap<>();

	public ManifestBytes(InputStream in) throws IOException {this(IOUtil.read(in));}
	@SuppressWarnings("fallthrough")
	public ManifestBytes(byte[] data) {
		this.data = data;

		int i = 0, len = data.length;
		List<Entry> lines = new ArrayList<>(len/72);

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
							lines.add(new Entry(lineEnd, isEmptyLine ? last : i, ++i));
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

		for (int j = 1; j < lines.size(); j++) {
			Entry attr = lines.get(j);
			String name = attr.findName(data, lines.get(j-1));
			namedAttributes.computeIfAbsent(name, Helpers.fnArrayList()).add(attr);
		}
		mainAttribute = lines.get(0);
	}

	public byte[] digest(MessageDigest digest) {
		assert mainAttribute.offset == 0;
		digest.reset();
		digest.update(data, mainAttribute.offset, mainAttribute.startOfNext);
		return digest.digest();
	}

	public byte[] digest(MessageDigest digest, String name) {
		digest.reset();
		for (Entry section : namedAttributes.get(name)) {
			digest.update(data, section.offset, section.startOfNext-section.offset);
		}
		return digest.digest();
	}

	static final class Entry {
		int offset;
		final int endOfLastLine, endOfSection, startOfNext;

		Entry(int endOfLastLine, int endOfSection, int startOfNext) {
			this.endOfLastLine = endOfLastLine;
			this.endOfSection = endOfSection;
			this.startOfNext = startOfNext;
		}

		private String getLines(byte[] data) {
			int start = endOfLastLine;
			do {
				start++;
			} while (data[start] != '\n' || (start + 1 != data.length && data[start + 1] == ' '));
			return new String(data, start, endOfSection - start - 1);
		}

		void getLines(byte[] data, Map<String, String> map) {
			for (String line : LineReader.create(getLines(data))) {
				int pos = line.indexOf(": ");
				map.put(line.substring(0, pos), line.substring(pos+2));
			}
		}

		@SuppressWarnings("fallthrough")
		String findName(byte[] data, Entry p) {
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