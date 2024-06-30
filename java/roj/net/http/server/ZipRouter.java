package roj.net.http.server;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.net.http.Headers;
import roj.net.http.HttpUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * @author Roj234
 * @since 2021/2/16 11:17
 */
public class ZipRouter implements Router {
	public final ZipFile zip;
	private String prefix = "";

	public ZipRouter(String path) throws IOException {this.zip = new ZipFile(path);}
	public ZipRouter(ZipFile zf) {this.zip = zf;}
	public ZipRouter(ZipFile zf, String prefix) {this.zip = zf;this.prefix = prefix;}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		String url = req.path();

		boolean flag = url.isEmpty() || url.endsWith("/");
		url = prefix.concat(url);

		ZEntry ze = zip.getEntry(flag ? url + "index.html" : url);
		if (ze == null) {
			if (flag) {
				ZEntry dir = zip.getEntry(url);
				if (dir != null && dir.getName().endsWith("/")) {
					rh.code(403);
					return StringResponse.simpleErrorPage(HttpUtil.FORBIDDEN);
				}
			}
			rh.code(404);
			return StringResponse.simpleErrorPage(HttpUtil.NOT_FOUND);
		}
		rh.code(200).headers("Cache-Control: max-age=86400");
		return FileResponse.response(req, new ZipFileInfo(zip, ze));
	}

	static final class ZipFileInfo implements FileInfo {
		final ZipFile zf;
		final ZEntry ze;

		ZipFileInfo(ZipFile zf, ZEntry ze) {
			if (ze.isEncrypted()) throw new IllegalArgumentException("encrypted");
			this.zf = zf;
			this.ze = ze;
		}

		@Override
		public int stats() { return ze.getMethod() == ZipEntry.DEFLATED ? FILE_DEFLATED|FILE_RA_DEFLATE : FILE_RA; }

		@Override
		public long length(boolean deflated) { return deflated ? ze.getCompressedSize() : ze.getSize(); }

		@Override
		public InputStream get(boolean deflated, long offset) throws IOException {
			InputStream in;
			if (deflated) {
				if (ze.getMethod() != ZipEntry.DEFLATED)
					throw new UnsupportedOperationException();
				in = zf.getFileStream(ze);
			} else {
				in = zf.getStream(ze);
			}
			in.skip(offset);
			return in;
		}

		@Override
		public long lastModified() { return ze.getModificationTime(); }

		@Override
		public void prepare(ResponseHeader srv, Headers h) { h.put("Content-Type", FileResponse.getMimeType(ze.getName())); }
	}
}