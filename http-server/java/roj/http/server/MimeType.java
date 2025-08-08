package roj.http.server;

import org.jetbrains.annotations.NotNull;
import roj.collect.XashMap;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.util.Locale;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/7/10 3:01
 */
public final class MimeType {
	private static MimeType fallback;
	static {
		try {
			loadMimeMap(IOUtil.getTextResourceIL("roj/http/server/mine.cfg"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static final XashMap<String, MimeType> REPOSITORY = Helpers.cast(XashMap.noCreation(MimeType.class, "ext").createSized(128));

	public final String ext;
	public String mime;
	public boolean compress;

	private MimeType _next;

	public MimeType(String ext, String mime) {
		this.ext = Objects.requireNonNull(ext);
		this.mime = Objects.requireNonNull(mime);
	}
	public MimeType(String ext, String mime, boolean compress) {
		this.ext = Objects.requireNonNull(ext);
		this.mime = Objects.requireNonNull(mime);
		this.compress = compress;
	}

	@NotNull
	public static MimeType get(String filename) {return REPOSITORY.getOrDefault(filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT), fallback);}
	@NotNull
	public static String getMimeType(String ext) {return get(ext).mime;}

	public static synchronized void loadMimeMap(String data) {
		REPOSITORY.clear();

		int i = 0;
		while (i < data.length()) {
			int lend = data.indexOf('\n', i);
			if (lend < 0) lend = data.length();

			if (data.charAt(i) != '#' && data.charAt(i) != '\n') {
				boolean zip = false;
				if (data.charAt(i) == 'z') {
					i += 2;
					zip = true;
				}

				int k = data.indexOf(' ', i);
				String mime = data.substring(i, k);
				i = k+1;

				int prevI = i;
				for (; i < lend; i++) {
					if (data.charAt(i) == ',') {
						REPOSITORY.add(new MimeType(data.substring(prevI, i), mime, zip));
						prevI = i+1;
					}
				}
				REPOSITORY.add(new MimeType(data.substring(prevI, lend), mime, zip));
			}

			i = lend+1;
		}

		if ((fallback = REPOSITORY.get("*")) == null)
			fallback = new MimeType("*", "application/octet-stream");
	}
}