package roj.crypt;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author Roj234
 * @since 2024/3/7 11:22
 */
public interface DerivablePrivateKey extends PrivateKey {
	PublicKey generatePublic();
}