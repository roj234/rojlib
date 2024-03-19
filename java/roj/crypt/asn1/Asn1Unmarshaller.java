package roj.crypt.asn1;

import roj.config.data.CEntry;

import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/4/6 0006 1:06
 */
public interface Asn1Unmarshaller extends Function<CEntry, CEntry> {}