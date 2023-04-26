package roj.net.mss;

import roj.crypt.FeedbackCipher;
import roj.crypt.RCipherSpi;

import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/14 17:30
 */
public class SimpleCipherFactory implements MSSCipherFactory {
	protected final int keySize, mcFlag;
	protected final Supplier<RCipherSpi> provider;

	public SimpleCipherFactory(int keySize, Supplier<RCipherSpi> provider) {
		this.keySize = keySize;
		this.provider = provider;
		this.mcFlag = 0;
	}

	public SimpleCipherFactory(int keySize, Supplier<RCipherSpi> provider, int mcFlag) {
		this.keySize = keySize;
		this.provider = provider;
		this.mcFlag = mcFlag;
	}

	@Override
	public int getKeySize() { return keySize; }

	@Override
	public RCipherSpi get() {
		RCipherSpi r = provider.get();
		if (mcFlag != 0) r = new FeedbackCipher(r, mcFlag);
		return r;
	}
}
