package roj.plugins.unpacker;

import roj.collect.TrieTree;
import roj.config.ConfigMaster;
import roj.config.mapper.Optional;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.text.ParseException;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.util.Collections;
import java.util.List;

/**
 * Har file exporter
 *
 * @author solo6975
 * @since 2022/1/1 19:20
 */
class Har implements Unpacker {
	TrieTree<byte[]> tree;

	@Override
	public TrieTree<?> load(File file) throws IOException {
		PojoHar har;
		try {
			har = ConfigMaster.JSON.readObject(PojoHar.class, file).log;
		} catch (ParseException e) {
			throw new IOException("无法解析文件", e);
		}

		System.out.println("Version "+har.version+" Created by "+har.creator);
		tree = new TrieTree<>();

		ByteList tmp = IOUtil.getSharedByteBuf();
		for (PojoHarItem entry : har.entries) {
			var content1 = entry.response.content;
			if (content1 == null || content1.text == null) continue;

			String url = entry.request.url;
			try {
				URL url1 = new URL(url);
				url = url1.getHost() + url1.getPath();
				if (url.endsWith("/")) url += "index.html";
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

			String text = content1.text, encoding = content1.encoding;

			tmp.clear();

			if (!text.isEmpty() && encoding.equalsIgnoreCase("base64")) {
				Base64.decode(text, tmp);
			} else if (encoding.isEmpty()) {
				tmp.putUTFData(text);
			} else {
				System.err.println(url+": 未知的编码方式: "+encoding);
			}

			tree.put(url, tmp.toByteArray());
		}
		return null;
	}

	@Override
	public void export(File path, String prefix) throws IOException {
		tree.forEachSince(prefix, (k, v) -> {
			File file = new File(path, k.toString());
			file.getParentFile().mkdirs();

			try {
				IOUtil.createSparseFile(file, v.length);
				try (var out = new FileOutputStream(file)) {
					out.write(v);
				}
			} catch (IOException e) {
				Helpers.athrow(e);
			}

			return FileVisitResult.CONTINUE;
		});
	}

	@Optional
	static final class PojoHar {
		PojoHar log;

		String version;
		PojoHarCreator creator;
		List<PojoHarItem> entries;
	}
	@Optional
	static final class PojoHarCreator {
		String name, version;
		public String toString() { return name+" "+version; }
	}
	@Optional
	static final class PojoHarItem {
		PojoHarReqRep request, response;
	}
	@Optional
	static final class PojoHarReqRep {
		PojoHarReqRep content;
		List<PojoHarHeader> headers = Collections.emptyList();

		String url;

		int size;
		String text, encoding = "";
	}
	static final class PojoHarHeader {
		String name, value;
	}
}