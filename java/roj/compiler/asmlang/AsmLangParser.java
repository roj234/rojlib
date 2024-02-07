package roj.compiler.asmlang;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyBitSet;
import roj.collect.ToIntMap;
import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.config.data.Node;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/8 0008 16:35
 */
public class AsmLangParser {
	static final class ByteCodeTokenizer extends Tokenizer {
		private static final short STACK = 10, LOCAL = 11, LINE = 12, FRAME = 13, EXCEPTION = 14, LABEL = 15, THIS = 16, TO_HANDLER = 17;
		private static final MyBitSet BC_LEND = new MyBitSet();
		private static final TrieTree<Word> BC_TID = new TrieTree<>();

		static {
			addSymbols(BC_TID, null, STACK, TextUtil.split1(".stack .local .line .frame .exception : this =>", ' '));
			for (ToIntMap.Entry<CharSequence> entry : Opcodes.opcodeByName().selfEntrySet()) {
				String key = entry.getKey().toString();
				Word wd = new Word().init(entry.v+100, 0, key);
				BC_TID.put(key, wd);
				BC_TID.put(key.toUpperCase(), wd);
				BC_TID.put(key.toLowerCase(), wd);
			}
			addWhitespace(BC_LEND);
		}

		@Override
		protected Word readDigit(boolean sign) throws ParseException { return digitReader(sign, DIGIT_HBO|DIGIT_DFL); }

		public List<Node> querySelectorAll(String key) {
			Tokenizer x = new Tokenizer().tokenIds(BC_TID).literalEnd(BC_LEND).init(key);
			return null;
		}
	}
	public static void main(String[] args) {
		TypeHelper.TYPE_TOSTRING_NO_PACKAGE = false;
		ConstantData data = Parser.parseConstants(CodeWriter.class);
		//CharList sb = IOUtil.getSharedCharBuf();
		//new AsmLang().toAsmLang(data, sb);
		System.out.println(data.toAsmLang(new CharList()));
	}
}