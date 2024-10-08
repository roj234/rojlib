package roj.net.http.server;

import org.jetbrains.annotations.NotNull;
import roj.collect.XHashSet;
import roj.util.Helpers;

import java.util.Locale;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/7/10 0010 3:01
 */
public final class MimeType {
	// 在传递过程中，XHashSet.Shape<String, Mime>的K参数（String）被javac直接擦除到了Object，这是Lavac已解决的问题
	private static MimeType fallback = new MimeType("*", "application/octet-stream");
	public static final XHashSet<String, MimeType> REPOSITORY = Helpers.cast(XHashSet.noCreation(MimeType.class, "ext").createSized(128));

	public final String ext;
	public String mime;
	public boolean zip;

	private MimeType _next;

	public MimeType(String ext, String mime) {
		this.ext = Objects.requireNonNull(ext);
		this.mime = Objects.requireNonNull(mime);
	}
	public MimeType(String ext, String mime, boolean zip) {
		this.ext = Objects.requireNonNull(ext);
		this.mime = Objects.requireNonNull(mime);
		this.zip = zip;
	}

	@NotNull
	public static MimeType get(String filename) {return REPOSITORY.getOrDefault(filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT), fallback);}
	@NotNull
	public static String getMimeType(String ext) {return get(ext).mime;}

	public static synchronized void loadMimeMap(String map) {
		REPOSITORY.clear();

		int i = 0;
		while (i < map.length()) {
			int lend = map.indexOf('\n', i);
			if (lend < 0) lend = map.length();

			if (map.charAt(i) != '#' && map.charAt(i) != '\n') {
				boolean zip = false;
				if (map.charAt(i) == 'z') {
					i += 2;
					zip = true;
				}

				int k = map.indexOf(' ', i);
				String mime = map.substring(i, k);
				i = k+1;

				int prevI = i;
				for (; i < lend; i++) {
					if (map.charAt(i) == ',') {
						REPOSITORY.add(new MimeType(map.substring(prevI, i), mime, zip));
						prevI = i+1;
					}
				}
				REPOSITORY.add(new MimeType(map.substring(prevI, lend), mime, zip));
			}

			i = lend+1;
		}

		if ((fallback = REPOSITORY.get("*")) == null)
			fallback = new MimeType("*", "application/octet-stream");
	}
}