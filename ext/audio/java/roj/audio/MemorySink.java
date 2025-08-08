package roj.audio;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;

/**
 * 将解码得到的PCM数据写入内存缓冲区
 */
public class MemorySink implements AudioSink {
	public AudioFormat format;
	public DynByteBuf buf;
	public MemorySink() {this.buf = new ByteList();}
	public MemorySink(DynByteBuf buf) {this.buf = buf;}

	@Override public void open(AudioFormat h) {
		this.format = h;
		this.buf.clear();
	}
	@Override public void write(byte[] b, int off, int len) {buf.put(b, off, len);}
}