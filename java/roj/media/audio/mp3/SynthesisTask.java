package roj.media.audio.mp3;

import roj.concurrent.task.ITask;

/**
 * 由于大量浮点运算，多相合成滤波耗时最多，使用并发运算加速解码
 */
final class SynthesisTask implements ITask {
	private final int ch;
	private final float[] samples;
	private float[][] bufA, bufB; // 调用者无锁双缓冲
	private final Layer3 owner;

	private volatile boolean done;

	public SynthesisTask(Layer3 owner, int ch) {
		this.owner = owner;
		this.ch = ch;
		this.done = true;
		samples = new float[32];
		bufA = new float[owner.granules][32 * 18];
		bufB = new float[owner.granules][32 * 18];
	}

	float[][] swapBuf() {
		float[][] p = bufA;
		bufA = bufB;
		bufB = p;

		done = false;

		return bufA;
	}

	float[][] getEmptyBuffer() {
		return bufA;
	}

	@Override
	public void execute() {
		if (done) return;

		int granules = owner.granules;
		Synthesis syth = owner.synthesis;

		float[][] bufB = this.bufB;
		float[] samples = this.samples;
		for (int gr = 0; gr < granules; gr++) {
			float[] xr = bufB[gr];
			for (int j = 0; j < 18; j += 2) {
				int sub;
				int i;
				for (i = j, sub = 0; sub < 32; sub++, i += 18)
					samples[sub] = xr[i];
				syth.synthesisSubBand(samples, ch);

				for (i = j + 1, sub = 0; sub < 32; sub += 2, i += 36) {
					samples[sub] = xr[i];

					// 多相频率倒置(INVERSE QUANTIZE SAMPLES)
					samples[sub + 1] = -xr[i + 18];
				}
				syth.synthesisSubBand(samples, ch);
			}
		}

		synchronized (this) {
			done = true;
			notify();
		}
	}

	@Override
	public boolean cancel() {
		synchronized (this) {
			done = true;
			notify();
		}
		return true;
	}

	void await() throws InterruptedException {
		synchronized (this) {
			if (!done) {
				wait();
			}
		}
	}
}