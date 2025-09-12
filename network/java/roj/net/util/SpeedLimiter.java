package roj.net.util;

import roj.config.mapper.Optional;

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
		this.lastRefill = System.currentTimeMillis();
		this.tokens = setting.maxTokens;
	}

	public static class Setting {
		@Optional
		public int minPacketSize, maxLatencyMs;
		public float refillRatePerMs;
		public int maxTokens;

		public Setting() {
			minPacketSize = 1400;
			maxLatencyMs = 10;
		}
		public Setting(int minPacketSize, int maxLatencyMs, float refillRatePerSecond, int maxTokens) {
			this.minPacketSize = minPacketSize;
			this.maxLatencyMs = maxLatencyMs;
			this.refillRatePerMs = refillRatePerSecond / 1000f;
			this.maxTokens = maxTokens;
		}
	}

	public int limit(int pendingBytes) {
		var timeDiff = System.currentTimeMillis() - lastRefill;
		int minCost = setting.maxLatencyMs != 0 && timeDiff > setting.maxLatencyMs ? 0 : setting.minPacketSize;
		return consume(setting, minCost, pendingBytes);
	}

	public boolean consume(Setting setting, int cost) {return consume(setting, cost, cost) != 0;}
	/**
	 * 消耗令牌，最少消耗minCost个，最多消耗maxCost个，如果令牌&gt;minCost但&lt;maxCost就全部消耗
	 */
	public int consume(Setting setting, int minCost, int maxCost) {
		long now = System.currentTimeMillis();
		long elapsed = now - lastRefill;
		int tokens1 = tokens;
		if (elapsed >= 0) {
			float increment = elapsed * setting.refillRatePerMs;
			int refills = (int) increment;
			if (refills > 0) {
				// 补充令牌
				tokens1 += refills;
				if (tokens1 < 0/* 如果经过了很长的时间导致加法溢出 */ || tokens1 > setting.maxTokens)
					tokens1 = setting.maxTokens;
					// 令牌未满时，将小数部分转换为时间差
				else now -= (long) ((increment - refills) / setting.refillRatePerMs);
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

		this.tokens = tokens1;
		return 0;
	}
}
