package roj.crypt.cert;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Unmodifiable;
import roj.config.node.IntArrayValue;
import roj.crypt.asn1.DerValue;
import roj.crypt.asn1.DerWriter;
import roj.crypt.asn1.KnownOID;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.Extension;

/**
 * @author Roj234
 * @since 2025/09/07 08:43
 */
public final class CertExtension implements Extension {
	public static CertExtension extendedKeyUsage(KnownOID... usages) {
		var dw = new DerWriter();
		dw.begin(DerValue.SEQUENCE);
		for (var oid : usages) {
			oid.assertType("extendedKeyUsage");
			dw.writeOid(oid.oid.value);
		}
		dw.end();
		return new CertExtension(KnownOID.extendedKeyUsage, false, dw.toByteArray());
	}

	public static CertExtension dnsName(String... domains) {
		var dw = new DerWriter();
		dw.begin(DerValue.SEQUENCE);
		for (var domain : domains) {
			dw.write(0x82, domain.getBytes(StandardCharsets.UTF_8));
		}
		dw.end();
		return new CertExtension(KnownOID.SubjectAlternativeName, false, dw.toByteArray());
	}

	public static CertExtension basicConstraints(boolean isCA, long pathLengthConstraints) {
		var dw = new DerWriter();
		dw.begin(DerValue.SEQUENCE);
		dw.writeBool(isCA);
		dw.writeInt(BigInteger.valueOf(pathLengthConstraints));
		dw.end();
		return new CertExtension(KnownOID.BasicConstraints, true, dw.toByteArray());
	}

	public static final int
			digitalSignature = 0x01,
			nonRepudiation = 0x02,
			keyEncipherment = 0x04,
			dataEncipherment = 0x08,
			keyAgreement = 0x10,
			keyCertSign = 0x20,
			cRLSign = 0x40,
			encipherOnly = 0x80,
			decipherOnly = 0x100;

	public static CertExtension keyUsage(@MagicConstant(flags = {
			digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment, keyAgreement, keyCertSign, cRLSign, encipherOnly, decipherOnly
	}) int keyUsage) {
		var dw = new DerWriter();
		int bits = 32 - Integer.numberOfLeadingZeros(keyUsage);
		int pad = (8 - (bits & 7)) & 7;
		keyUsage <<= pad;
		dw.writeBits(bits > 8 ? new byte[]{(byte) (keyUsage >>> 8), (byte) keyUsage} : new byte[]{(byte) keyUsage}, bits);
		return new CertExtension(KnownOID.KeyUsage, true, dw.toByteArray());
	}

	private final KnownOID knownOID;
	private final IntArrayValue OID;
	private final boolean isCritical;
	private final byte[] data;

	public CertExtension(KnownOID oid, boolean isCritical, byte[] data) {
		oid.assertType("CertExt");
		knownOID = oid;
		OID = knownOID.oid;
		this.isCritical = isCritical;
		this.data = data;
	}
	public CertExtension(IntArrayValue oid, boolean isCritical, byte[] data) {
		knownOID = null;
		OID = oid;
		this.isCritical = isCritical;
		this.data = data;
	}


	public String toString() {return knownOID == null ? getId() : knownOID.name();}
	@Override public String getId() {return OID.toString();}
	public IntArrayValue getOID() {return OID;}

	@Override public boolean isCritical() {return isCritical;}
	@Unmodifiable @Override public byte[] getValue() {return data;}
	@Override public void encode(OutputStream out) throws IOException {out.write(data);}
}
