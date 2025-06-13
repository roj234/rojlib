package roj.plugins.web.share;

import roj.collect.ArrayList;
import roj.concurrent.OperationDone;
import roj.config.auto.Optional;
import roj.io.IOUtil;
import roj.plugin.VFSRouter;
import roj.reflect.Unaligned;

import java.io.File;
import java.util.List;
import java.util.Set;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2025/4/4 21:27
 */
final class Share {
	String id, name;
	long time;
	long size;
	@Optional transient int view, download;
	@Optional volatile long expire;
	@Optional String text;
	@Optional String code;
	@Optional transient File base;
	@Optional List<ShareFile> files;
	transient int owner;
	transient Set<ChunkUpload.Task> uploading;
	transient volatile VFSRouter vfs;

	public int expireType() {return expire == 0 ? 0 : expire > 100000 ? 1 : 2;}
	public boolean isExpired() {return expireType() == 1 && System.currentTimeMillis() > expire;}

	public void fillFromServerPath(File path) {
		files = new ArrayList<>();
		size = 0;
		base = path;
		int prefixLength = path.getAbsolutePath().length() + 1;
		try {
			IOUtil.listFiles(path, file -> {
				if (files.size() == FileShare.LOCAL_FILE_MAX) throw OperationDone.INSTANCE;
				ShareFile file1 = new ShareFile(file, prefixLength);
				size += file1.size;
				files.add(file1);
				return false;
			});
		} catch (OperationDone ignored) {
		}
	}

	transient Share _next;

	static final long EXPIRE_OFFSET = Unaligned.fieldOffset(Share.class, "expire");

	public void countDown(long timeout) {
		while (true) {
			long oldVal = this.expire;
			long newVal = oldVal == 1 ? timeout : oldVal - 1;
			if (U.compareAndSwapLong(this, EXPIRE_OFFSET, oldVal, newVal)) {
				if (oldVal == 1) code = ""; // no new entry
				break;
			}
		}
	}
}
