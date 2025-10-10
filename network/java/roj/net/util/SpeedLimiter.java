package roj.net.util;

import roj.config.mapper.Name;
import roj.config.mapper.Optional;
import roj.config.mapper.Via;
import roj.net.SelectorLoop;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2025/4/29 12:10
 */
public class SpeedLimiter {
	protected final Setting setting;
	protected long lastRefill;
	protected int tokens;

	public SpeedLimiter(Setting setting) {
		this.setting = setting;
		this.lastRefill = System.currentTimeMillis() * 1000000L;
		this.tokens = setting.maxTokens;
	}

	public static class Setting {
		@Optional
		public int minPacketSize, maxLatencyMs;
		@Name("speed")
		@Via(get = "getRefillRate", set = "setRefillRate")
		public float refillRate;
		@Name("burst")
		@Via(get = "getMaxTokens", set = "setMaxTokens")
		public int maxTokens;

		public String getRefillRate() {return refillRate == 0 ? "0" : refillRate+"/ns";}
		public void setRefillRate(String refillRate) {
			if (refillRate.equals("0")) {
				this.refillRate = 0;
				return;
			}

			int pos = refillRate.indexOf('/');
			if (pos < 0) throw new IllegalArgumentException("missing unit: "+refillRate);
			var base = TextUtil.unscaledNumber1024(refillRate.substring(0, pos));
			switch (refillRate.substring(pos+1)) {
				case "s" -> base *= 1e-9;
				case "ms" -> base *= 1e-6;
				case "us" -> base *= 1e-3;
				case "ns" -> base *= 1e-0;
				default -> throw new IllegalArgumentException("unsupported unit: "+refillRate);
			}
			this.refillRate = (float) base;
		}

		public String getMaxTokens() {return TextUtil.scaledNumber(maxTokens);}
		public void setMaxTokens(String unit) {maxTokens = (int) TextUtil.unscaledNumber1024(unit);}

		public Setting() {
			// 满足最大延迟条件时，发送的数据包不小于该大小
			minPacketSize = 1200;
			// 每隔至少该毫秒发送一个数据包
			maxLatencyMs = 50;
		}
		public Setting(int minPacketSize, int maxLatencyMs, float refillRatePerSecond, int maxTokens) {
			this.minPacketSize = minPacketSize;
			this.maxLatencyMs = maxLatencyMs;
			this.refillRate = refillRatePerSecond / 1000f;
			this.maxTokens = maxTokens;
		}
	}

	public int tryAcquire(int maxCost) {
		var timeDiff = SelectorLoop.currentTimeMillis() - lastRefill / 1000000L;
		int minCost = setting.maxLatencyMs != 0 && timeDiff >= setting.maxLatencyMs ? 0 : setting.minPacketSize;
		return consume(setting, minCost, maxCost);
	}

	public void returnTokens(int returned) {tokens += returned;}

	/**
	 * 消耗令牌，最少消耗minCost个，最多消耗maxCost个，如果令牌>minCost但<maxCost就全部消耗
	 */
	private int consume(Setting setting, int minCost, int maxCost) {
		long now = SelectorLoop.currentTimeMillis() * 1000000L;
		long elapsed = now - lastRefill;
		int tokens1 = tokens;
		if (elapsed >= 0) {
			float increment = elapsed * setting.refillRate;
			int refills = (int) Math.min(Integer.MAX_VALUE, increment);
			if (refills > 0) {
				// 补充令牌
				tokens1 += refills;
				if (tokens1 < 0/* 如果经过了很长的时间导致加法溢出 */ || tokens1 > setting.maxTokens)
					tokens1 = setting.maxTokens;
				// 令牌未满时，将小数部分转换为时间差
				else now -= (long) ((increment - refills) / setting.refillRate);
			}
		} else {
			lastRefill = now;
		}

		if (tokens1 >= minCost) {
			int consumed = Math.min(tokens1, maxCost);
			lastRefill = now;
			tokens = tokens1 - consumed;
			return consumed;
		}

		return 0;
	}
}
