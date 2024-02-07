package roj.asmx.mapper.util;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.zip.ZipFileWriter;
import roj.concurrent.task.AsyncTask;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class ResWriter extends AsyncTask<Void> {
	public ResWriter(ZipFileWriter zfw, Map<ArchiveEntry, ArchiveFile> resources) {
		this.zfw = zfw;
		this.resources = resources;
	}

	private final ZipFileWriter zfw;
	private final Map<ArchiveEntry, ArchiveFile> resources;

	@Override
	protected Void invoke() throws Exception {
		for (Map.Entry<ArchiveEntry, ArchiveFile> entry : resources.entrySet()) {
			zfw.copy(entry.getValue(), entry.getKey());
		}
		return null;
	}
}