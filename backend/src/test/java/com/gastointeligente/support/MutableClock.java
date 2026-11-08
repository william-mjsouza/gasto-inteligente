package com.gastointeligente.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Relógio de teste: parte do instante real e só avança quando o teste manda. */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

	public void advance(Duration duration) {
		this.now.updateAndGet((instant) -> instant.plus(duration));
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return this.now.get();
	}

}
