package ilib.asm.rpl;

import roj.io.IOUtil;
import roj.io.MyRegionFile;
import roj.util.ByteList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj233
 * @since 2022/5/13 3:23
 */
public class FastChunkSL extends MyRegionFile {
	ReentrantReadWriteLock lock;

	public FastChunkSL(File file) throws IOException {
		super(file);
		lock = new ReentrantReadWriteLock();
	}

	@Deprecated
	protected synchronized void func_76706_a(int x, int z, byte[] data, int length) {
		ByteList buf = IOUtil.getSharedByteBuf().put(DEFLATE).put(data, 0, length);
		lock.writeLock().lock();
		try {
			write(x + z * 32, buf);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean func_76709_c(int x, int z) {
		lock.readLock().lock();
		try {
			return hasData(x + z * 32);
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean chunkExists(int x, int z) {
		lock.readLock().lock();
		try {
			return hasData(x + z * 32);
		} finally {
			lock.readLock().unlock();
		}
	}

	public void func_76708_c() throws IOException {
		lock.writeLock().lock();
		try {
			close();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public DataInputStream func_76704_a(int x, int z) {
		try {
			lock.readLock().lock();
			return getDataInput(x + z * 32);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			lock.readLock().unlock();
		}
	}

	public DataOutputStream func_76710_b(int x, int z) {
		return getDataOutput(x + z * 32);
	}
}
