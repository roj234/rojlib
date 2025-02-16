package roj.test;

import roj.config.ParseException;
import roj.crypt.ConvolutionalECC;
import roj.crypt.MT19937;
import roj.crypt.ReedSolomonECC;
import roj.util.ByteList;

import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2025/3/21 0021 9:37
 */
public class ECCTest {

	public static void main(String[] args) throws ParseException {
		ConvolutionalECC cecc = new ConvolutionalECC(24,25);
		var rsecc = new ReedSolomonECC(255-32, 4);

		ByteList test = ByteList.wrap(
				("This is a very long string, and there are "+(100d*(1-(float) cecc.getInBits() / cecc.getOutBits()))+"% redundant data guarded by ConvolutionECC, "+100.0d*(1-rsecc.dataRate())+"% redundant data guarded by ReedSolomon ECC")
						.getBytes(StandardCharsets.UTF_8));
		System.out.println("\""+test+"\"");
		test.wIndex(rsecc.dataSize());
		rsecc.generateCode(test, test);
		test.rIndex = 0;
		System.out.print("| rsecc="+test.wIndex());

		ByteList tmp = new ByteList();
		cecc.init(tmp);
		cecc.encode(test);
		cecc.encodeFinish();
		System.out.println("| cecc="+tmp.wIndex());

		float rate = Float.parseFloat(args[0]);
		System.out.println("\n随机翻转"+(rate*100)+"%的bit, SNR="+10*Math.log10((1-rate)/rate)+"dB\n");
		test.rIndex = 0;
		test.wIndex(0x10);
		tmp.rIndex = 0;
		shuffle(tmp, rate);
		shuffle(test, rate);
		System.out.println("example if no protect="+test.dump());
		System.out.println();

		test.clear();
		cecc.init(test);
		cecc.decode(tmp);
		System.out.println(test.dump());
		int i = rsecc.errorCorrection(test);
		test.wIndex(rsecc.dataSize());
		System.out.println("\""+test+"\"\nCECC errorRate "+cecc.getSymbolError()+"/"+cecc.getSymbolCount()+"\nRSECC errorRate "+i+"/231");
	}

	private static void shuffle(ByteList tmp, float rate) {
		int len = tmp.wIndex();
		byte[] list = tmp.list;
		MT19937 rnd = new MT19937();
		for (int i = 0; i < len<<3; i++) {
			if (rnd.nextFloat() < rate)
				list[i>>>3] ^= (rnd.nextBoolean()?1:0)<<(i&7);
		}
	}

}
