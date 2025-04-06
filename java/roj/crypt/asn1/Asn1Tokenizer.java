package roj.crypt.asn1;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.Tokenizer;
import roj.config.Word;

/**
 * @author Roj234
 * @since 2024/3/22 0022 22:46
 */
final class Asn1Tokenizer extends Tokenizer {
	static final short
		AsnOid = 19, AsnAny = 20, AsnBoolean = 21,
		AsnSetOf = 22, AsnSequenceOf = 23,
		AsnSequence = 24, AsnSet = 25, AsnChoice = 26,
		AsnOptional = 27, AsnImplicit = 28, AsnExplicit = 29, AsnDefinedBy = 30;
	static final short
		lBrace = 31, rBrace = 32,
		lBracket = 33, rBracket = 34,
		lParen = 35, rParen = 36,
		comma = 37, is = 38, dot2 = 39;

	private static final TrieTree<Word> ASN1_TOKEN = new TrieTree<>();
	private static final MyBitSet ASN1_LEND = new MyBitSet();
	private static final Int2IntMap ASN1_C2C = new Int2IntMap();

	static {
		addKeywords(ASN1_TOKEN, 10, "INTEGER","BIT STRING","OCTET STRING","NULL","UTF8 STRING",
				"PRINTABLE STRING","IA5 STRING","UTCTime","GeneralizedTime","OBJECT IDENTIFIER","ANY","BOOLEAN",
				"SET OF","SEQUENCE OF","SEQUENCE","SET","CHOICE","OPTIONAL","IMPLICIT","EXPLICIT","DEFINED BY");
		ASN1_TOKEN.put("--", new Word().init(0, ST_SINGLE_LINE_COMMENT, "--"));

		addSymbols(ASN1_TOKEN, ASN1_LEND, 31, "{", "}", "[", "]", "(", ")", ",", "::=", "..");
		addWhitespace(ASN1_LEND);

		ASN1_C2C.putAll(NUMBER_C2C);
		ASN1_C2C.remove('"');
		ASN1_C2C.remove('\'');
	}

	{
		tokens = ASN1_TOKEN;
		firstChar = ASN1_C2C;
		literalEnd = ASN1_LEND;
	}
}