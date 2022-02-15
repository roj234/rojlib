package roj.crypt;

import java.io.File;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;

/**
 * @author Roj233
 * @since 2022/2/17 19:15
 */
public interface PSKFile {
    static PSKFile getInstance(String format) {
        switch (format) {
            case "RSA":
                return new PSKRSA();
            default:
                return new PSKSimple(format);
        }
    }

    KeyPair loadOrGenerate(File allFile, File publicFile, byte[] pass) throws GeneralSecurityException;
    PublicKey load(File publicFile);
}
