package roj.http.server;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.http.Headers;
import roj.http.HttpUtil;
import roj.http.IllegalRequestException;
import roj.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

/**
 * @author Roj234
 * @since 2021/2/16 11:17
 */
public class ZipRouter implements Router, Predicate<String> {
	public final ZipFile zip;
	private String prefix = "";
	private String cacheControl = HttpUtil.CACHED;

	public ZipRouter(String path) throws IOException {this.zip = new ZipFile(path);}
	public ZipRouter(File path) throws IOException {this.zip = new ZipFile(path);}
	public ZipRouter(ZipFile zf) {this.zip = zf;}
	public ZipRouter(ZipFile zf, String prefix) {this.zip = zf;this.prefix = prefix;}

	protected String getCacheControl(ZEntry ze) {return cacheControl;}
	public ZipRouter setCacheControl(String var) {cacheControl = var;return this;}
	public String getPrefix() {return prefix;}
	public void setPrefix(String prefix) {this.prefix = prefix;}

	public static Response zip(Request req, ZipFile zf, ZEntry ze) {return Response.file(req, new ZipFileInfo(zf, ze));}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		String url = req.path();

		boolean isDir = url.isEmpty() || url.endsWith("/");
		url = prefix.concat(url);

		ZEntry ze = zip.getEntry(isDir ? url+"index.html" : url);
		if (ze == null) {
			if (isDir) {
				ze = zip.getEntry(url);
				if (ze != null && ze.getName().endsWith("/")) {
					rh.code(403);
					return Response.httpError(HttpUtil.FORBIDDEN);
				}
			}
			rh.code(404);
			return Response.httpError(HttpUtil.NOT_FOUND);
		}

		rh.code(200).header("cache-control", getCacheControl(ze));
		if (ze.isEncrypted()) throw new IllegalRequestException(500, "该文件已加密");
		return Response.file(req, new ZipFileInfo(zip, ze));
	}

	// (Optional) for OKRouter Prefix Delegation check
	@Override
	public boolean test(String url) {
		boolean isDir = url.isEmpty() || url.endsWith("/");
		url = prefix.concat(url);

		return zip.getEntry(url) != null || (isDir && zip.getEntry(url+"index.html") != null);
	}

	public static final class ZipFileInfo implements FileInfo {
		final ZipFile zf;
		final ZEntry ze;

		public ZipFileInfo(ZipFile zf, ZEntry ze) {
			this.zf = zf;
			this.ze = ze;
		}

		@Override
		public int stats() { return ze.getMethod() == ZipEntry.DEFLATED ? FILE_DEFLATED : FILE_RA; }

		@Override
		public long length(boolean deflated) { return deflated ? ze.getCompressedSize() : ze.getSize(); }

		@Override
		public InputStream get(boolean deflated, long offset) throws IOException {
			InputStream in;
			if (deflated) {
				assert ze.getMethod() == ZipEntry.DEFLATED;
				in = zf.getRawStream(ze);
			} else {
				in = zf.getStream(ze);
			}
			IOUtil.skipFully(in, offset);
			return in;
		}

		@Override
		public long lastModified() {return ze.getModificationTime();}
		@Override
		public String getETag() {return '"'+Integer.toUnsignedString(ze.getCrc32(), 36)+'"';}

		@Override
		public void prepare(ResponseHeader rh, Headers h) {h.put("content-type", MimeType.getMimeType(ze.getName()));}
	}
}