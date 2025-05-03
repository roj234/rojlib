package roj.net;

import java.nio.channels.FileChannel;

/**
 * @author Roj234
 * @since 2024/7/15 1:53
 */
public final class SendfilePkt {
	public FileChannel channel;
	public long offset, length;
	public long written;

	public SendfilePkt(FileChannel fch) {this.channel = fch;}
	public SendfilePkt(FileChannel fch, long offset, long len) {
		this.channel = fch;
		this.offset = offset;
		this.length = len;
	}
	public boolean flip() {
		if (written <= 0) return false;
		offset += written;
		length -= written;
		return true;
	}

	@Override
	public String toString() {return "Sendfile{"+"ch="+channel+", off="+offset+", len="+length+", written="+written+'}';}
}