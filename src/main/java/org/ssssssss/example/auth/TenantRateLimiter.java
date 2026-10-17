package org.ssssssss.example.auth;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按租户（tenantId）的固定窗口限流器：每个 1 秒窗口内最多允许 {@code maxPerSecond} 次请求。
 *
 * <p>{@code maxPerSecond <= 0} 时表示不限流。</p>
 */
public class TenantRateLimiter {

	private static final int CLEANUP_INTERVAL_SECONDS = 60;
	private static final int IDLE_EXPIRE_SECONDS = 60;

	private final int maxPerSecond;
	private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
	private volatile long lastCleanupSecond = 0L;

	public TenantRateLimiter(int maxPerSecond) {
		this.maxPerSecond = maxPerSecond;
	}

	/**
	 * 尝试为指定租户获取一次请求许可。
	 *
	 * @param tenantId 租户 ID
	 * @return {@code true} 放行；{@code false} 当前窗口已耗尽
	 */
	public boolean tryAcquire(long tenantId) {
		if (maxPerSecond <= 0) {
			return true;
		}
		long nowSecond = System.currentTimeMillis() / 1000L;
		Window window = windows.computeIfAbsent(tenantId, key -> new Window());
		synchronized (window) {
			if (window.windowSecond != nowSecond) {
				window.windowSecond = nowSecond;
				window.count = 0;
			}
			if (window.count >= maxPerSecond) {
				return false;
			}
			window.count++;
		}
		cleanupIdle(nowSecond);
		return true;
	}

	private void cleanupIdle(long nowSecond) {
		if (nowSecond - lastCleanupSecond < CLEANUP_INTERVAL_SECONDS) {
			return;
		}
		lastCleanupSecond = nowSecond;
		long cutoff = nowSecond - IDLE_EXPIRE_SECONDS;
		windows.entrySet().removeIf(entry -> entry.getValue().windowSecond < cutoff);
	}

	private static final class Window {
		private volatile long windowSecond = -1L;
		private long count = 0L;
	}
}
