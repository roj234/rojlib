package roj.net.mss;

import roj.crypt.CipheR;
import roj.crypt.MyCipher;

import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/14 17:30
 */
public class SimpleCipherFactory implements MSSCipherFactory {
	protected final int keySize, mcFlag;
	protected final Supplier<CipheR> provider;

	public SimpleCipherFactory(int keySize, Supplier<CipheR> provider) {
		this.keySize = keySize;
		this.provider = provider;
		this.mcFlag = 0;
	}

	public SimpleCipherFactory(int keySize, Supplier<CipheR> provider, int mcFlag) {
		this.keySize = keySize;
		this.provider = provider;
		this.mcFlag = mcFlag;
	}

	@Override
	public int getKeySize() {
		return keySize;
	}

	@Override
	public CipheR get() {
		CipheR r = provider.get();
		if (mcFlag != 0) r = new MyCipher(r, mcFlag);
		return r;
	}
}
