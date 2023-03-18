package roj.net.mss;

import java.security.SecureRandom;
import java.security.Signature;

/**
 * @author solo6975
 * @since 2022/2/13 13:06
 */
public interface MSSPrivateKey extends MSSPublicKey {
	Signature signer(SecureRandom random);
	int format();
}
