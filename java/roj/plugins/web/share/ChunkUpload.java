package roj.plugins.web.share;

import org.jetbrains.annotations.Nullable;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.ConfigMaster;
import roj.crypt.Base64;
import roj.crypt.CryptoFactory;
import roj.http.server.*;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.text.CharList;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 分块上传
 * @author Roj234
 * @since 2024/8/6 0:07
 */
public class ChunkUpload {
	public static final class Task {
		long start = System.currentTimeMillis();

		transient BiConsumer<Task, Boolean> callback;
		transient int id, sk;

		long length;
		File lock, tmp;
		transient SimpleList<FileChannel> fds = new SimpleList<>();

		MyBitSet bitmap;
		int fragmentSize, fragmentCount, threads;

		public File getFile() {return tmp;}

		synchronized void finish(boolean success) throws IOException {
			if (fds == null) return;

			var fds1 = fds;
			fds = null;
			for (var fc : fds1) IOUtil.closeSilently(fc);

			lock.delete();

			String path = tmp.getAbsolutePath();
			File dest = new File(path.substring(0, path.lastIndexOf('.')));
			if (success ? tmp.renameTo(dest) : tmp.delete()) {
			} else {
				throw new IOException("Rename failed");
			}

			if (success) tmp = dest;
			else lock = null;

			if (callback != null) callback.accept(this, success);
		}

		synchronized void closeFc(FileChannel fc) {
			if (fds == null || fds.size() > 4) {
				IOUtil.closeSilently(fc);
				if (fds == null && lock == null) tmp.delete();
			} else fds.add(fc);

			threads++;
		}
		synchronized FileChannel getFc() throws IOException {
			if (threads == 0) throw new FastFailException("线程数超过限制");
			threads--;
			return fds.isEmpty() ? FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE) : fds.pop();
		}
	}

	private static final Logger LOGGER = Logger.getLogger("分片上传");
	private final IntMap<Task> tasks = new IntMap<>();
	private final MyBitSet taskIds = new MyBitSet();
	private final SecureRandom srnd = new SecureRandom();
	private int fragmentSize = 4194304, threads = 4;

	public ChunkUpload() {}
	public void setFragmentSize(int fragmentSize) {this.fragmentSize = fragmentSize;}
	public void setThreads(int threads) {this.threads = threads;}

	public Content newTask(File path, String name, long size, @Nullable BiConsumer<Task, Boolean> callback) {
		File lock = new File(path, name+".tmp.cfg");
		File tmpFile = new File(path, name+".tmp");

		Task task;

		noCreation: {
			try {
				if (lock.isFile()) {
					task = ConfigMaster.JSON.readObject(Task.class, lock);
					break noCreation;
				}
			} catch (Exception e) {
				LOGGER.warn("任务反序列化失败", e);
			}

			task = new Task();
			task.length = size;
			task.threads = threads;
			task.fragmentSize = fragmentSize;
			int fragmentCount = (int) ((size+task.fragmentSize-1) / task.fragmentSize);
			task.fragmentCount = fragmentCount;
			task.bitmap = new MyBitSet(fragmentCount);
			task.bitmap.fill(fragmentCount);
			task.lock = lock;
			task.tmp = tmpFile;

			try {
				IOUtil.createSparseFile(tmpFile, size);
			} catch (Exception e) {
				LOGGER.warn("初始化稀疏文件失败", e);
				return Content.internalError("任务创建失败", e);
			}
		}

		if (callback != null) {
			callback.accept(task, null);
			task.callback = callback;
		}

		synchronized (tasks) {
			task.id = taskIds.nextFalse(0);
			taskIds.add(task.id);
			tasks.putInt(task.id, task);
		}

		return Content.json("{\"ok\":true,\"id\":\""+getId(task)+"\",\"fragment\":"+task.fragmentSize+",\"threads\":"+task.threads+"}");
	}

	public CharSequence getUploadStatus(Request req) throws IllegalRequestException {
		var task = getTask(req.argument("taskId"));
		if (task == null) return "{\"ok\":false,\"data\":\"任务不存在\"}";
		var sb = new CharList("{\"ok\":true,\"data\":[");
		synchronized (task) {
			var itr = task.bitmap.iterator();
			if (itr.hasNext()) while (true) {
				sb.append(itr.nextInt());
				if (!itr.hasNext()) break;
				sb.append(',');
			}
		}
        return sb.append("]}");
	}

	public void uploadProcessor(Request req, PostSetting setting) throws IOException {
		var task = getTask(req.argument("taskId"));
		if (task == null) throw IllegalRequestException.BAD_REQUEST;
		int fragment = Integer.parseInt(req.argument("fragment"));
		if (fragment >= task.fragmentCount) throw IllegalRequestException.BAD_REQUEST;

		var fc = task.getFc().position((long) task.fragmentSize * fragment);
		var end = (fragment+1 == task.fragmentCount ? task.length : fc.position()+task.fragmentSize);

		long uploadSize = setting.postExceptLength();
		long acceptSize = end - fc.position();
		if (uploadSize >= 0 && acceptSize != uploadSize) throw IllegalRequestException.BAD_REQUEST;

		setting.postAccept(acceptSize, 60000);

		setting.postHandler(new BodyParser() {
			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				var buf = (DynByteBuf) msg;
				fc.write(buf.nioBuffer());
				buf.rIndex = buf.wIndex();
			}

			@Override
			public void onSuccess(DynByteBuf rest) throws IOException {
				if (fc.position() != end) throw new IOException("Size Error");
				synchronized (task) {
					task.bitmap.remove(fragment);
				}

				String msg;
				if (task.bitmap.size() == 0) {
					synchronized (tasks) {tasks.remove(task.id);}
					task.finish(true);
					msg = "0";
				} else {
					msg = String.valueOf(task.bitmap.size());
				}
				throw new IllegalRequestException(200, Content.text(msg));
			}

			@Override
			public void onComplete() throws IOException {
				task.closeFc(fc);
			}
		});
	}

	public void purge(long expire) {
		List<Task> purged = new SimpleList<>();
		synchronized (tasks) {
			for (var itr = tasks.values().iterator(); itr.hasNext(); ) {
				var task = itr.next();
				if (task.start < expire) {
					purged.add(task);
					itr.remove();
				}
			}
		}
		for (int i = 0; i < purged.size(); i++) {
			try {
				purged.get(i).finish(false);
			} catch (Exception e) {
				LOGGER.warn("结束任务出错", e);
			}
		}
	}

	private Task getTask(String taskId) {
		ByteList buf = IOUtil.getSharedByteBuf();
		Base64.decode(taskId, buf, Base64.B64_URL_SAFE_REV);
		if (buf.readableBytes() == 12) try {
			int taskIdNum = buf.readInt();
			int hash = buf.readInt(8);

			var task = tasks.get(taskIdNum);
			if (task != null && CryptoFactory.xxHash32(taskIdNum ^ task.sk, buf.list, 4, 4) == hash) {
				return task;
			}
		} catch (Exception ignored) {}
		return null;
	}
	private String getId(Task task) {
		task.sk = srnd.nextInt();
		var buf = IOUtil.getSharedByteBuf();
		return buf.putInt(task.id)
				  .putInt(srnd.nextInt())
				  .putInt(CryptoFactory.xxHash32(task.id ^ task.sk, buf.list, 4, 4))
				  .base64UrlSafe();
	}
}