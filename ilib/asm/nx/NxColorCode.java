package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.io.IOUtil;
import roj.text.CharList;

import net.minecraft.util.ChatAllowedCharacters;

/**
 * @author Roj233
 * @since 2022/5/10 19:25
 */
@Nixim("/")
class NxColorCode extends ChatAllowedCharacters {
	@Inject
	public static boolean isAllowedCharacter(char character) {
		return character >= ' ' && character != 127;
	}

	@Inject
	public static String filterAllowedCharacters(String input) {
		CharList tmp = IOUtil.getSharedCharBuf();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (isAllowedCharacter(c)) tmp.append(c);
		}
		return tmp.length() == input.length() ? input : tmp.toString();
	}
}
