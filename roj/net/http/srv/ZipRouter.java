package roj.net.http.srv;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.net.http.Code;
import roj.net.http.Headers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * @author Roj234
 * @since 2021/2/16 11:17
 */
public class ZipRouter implements Router {
	public final ZipArchive zipFs;

	public ZipRouter(String zipFs) throws IOException {
		this.zipFs = new ZipArchive(new File(zipFs), ZipArchive.FLAG_BACKWARD_READ);
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		String url = req.path().substring(1);

		boolean flag = req.path().endsWith("/");
		ZEntry ze = zipFs.getEntries().get(flag ? url + "index.html" : url);
		if (ze == null) {
			if (flag) {
				ZEntry dir = zipFs.getEntries().get(url);
				if (dir != null && dir.getName().endsWith("/")) {
					rh.code(403);
					return StringResponse.httpErr(Code.FORBIDDEN);
				}
			}
			rh.code(404);
			return StringResponse.httpErr(Code.NOT_FOUND);
		}
		rh.code(200).headers("Cache-Control: max-age=86400");
		return new FileResponse().init(req, new ZipFileInfo(zipFs, ze));
	}

	static final class ZipFileInfo implements FileInfo {
		final ZipArchive zf;
		final ZEntry ze;

		ZipFileInfo(ZipArchive zf, ZEntry ze) {
			if (ze.isEncrypted()) throw new IllegalArgumentException("encrypted");
			this.zf = zf;
			this.ze = ze;
		}

		@Override
		public int stats() {
			return ze.getMethod() == ZipEntry.DEFLATED ? CH_DEFL | CH_RA_DEFL : CH_RA_RAW;
		}

		@Override
		public long length(boolean deflated) {
			return deflated ? ze.getCompressedSize() : ze.getSize();
		}

		@Override
		public InputStream get(boolean deflated, long offset) throws IOException {
			InputStream in;
			if (deflated) {
				if (ze.getMethod() != ZipEntry.DEFLATED)
					throw new UnsupportedOperationException();
				in = zf.i_getRawData(ze);
			} else {
				in = zf.getStream(ze);
			}
			in.skip(offset);
			return in;
		}

		@Override
		public long lastModified() {
			return ze.getModificationTime();
		}

		@Override
		public void prepare(ResponseHeader srv, Headers h) {
			h.put("Content-Type", FileResponse.getMimeType(ze.getName()));
		}
	}
}
