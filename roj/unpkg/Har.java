package roj.unpkg;

import roj.config.ParseException;
import roj.config.serial.Optional;
import roj.config.serial.SerializerFactory;
import roj.config.serial.Serializers;
import roj.crypt.Base64;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Har file exporter
 *
 * @author solo6975
 * @since 2022/1/1 19:20
 */
public class Har {
	public static void main(String[] args) throws IOException, ParseException {
		if (args.length < 2) {
			System.out.println("HarExporter file store");
			return;
		}

		File base = new File(args[1]);
		SerializerFactory factory = Serializers.newSerializerFactory();
		PojoHar har = factory.deserialize(factory.adapter(PojoHar.class), "json", new File(args[0])).log;
		System.out.println("Version " + har.version + " Created by " + har.creator);
		ByteList tmp = new ByteList();
		for (PojoHarItem entry : har.entries) {
			PojoHarReqRep content1 = entry.response.content;
			if (content1 == null || content1.size == 0) continue;

			String url = entry.request.url;
			try {
				URL url1 = new URL(url);
				url = url1.getHost() + url1.getPath();
				if (url.endsWith("/")) url += "index.html";
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

			String content = content1.text;
			String encoding = content1.encoding;

			tmp.clear();
			if (!content.isEmpty() && encoding.equalsIgnoreCase("base64")) {
				Base64.decode(content, tmp);
			} else if (encoding.isEmpty()) {
				tmp.putUTFData(content);
			} else {
				System.err.println("未知的编码方式for " + url + ": " + encoding);
			}

			File file = new File(base, url);
			file.getParentFile().mkdirs();
			try (FileOutputStream fos = new FileOutputStream(file)) {
				tmp.writeToStream(fos);
				System.out.println("OK " + url);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
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