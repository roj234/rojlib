package roj.media.audio;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;

/**
 * 将解码得到的PCM数据写入内存缓冲区
 */
public class MemoryAudioOutput implements AudioOutput {
	public AudioFormat format;
	public DynByteBuf buf;
	public MemoryAudioOutput() {this.buf = new ByteList();}
	public MemoryAudioOutput(DynByteBuf buf) {this.buf = buf;}

	@Override
	public void init(AudioFormat h, int buffer) {
		this.format = h;
		this.buf.clear();
	}
	@Override public void close() {}
	@Override public int write(byte[] b, int off, int len, boolean blocking) {buf.put(b, off, len);return len;}
}