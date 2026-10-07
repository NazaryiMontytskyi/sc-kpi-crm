package com.kpi.studentcouncil.crm.files.internal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Fails with {@code FILE_TOO_LARGE} as soon as more than {@code limit} bytes have been read. */
class LimitedInputStream extends FilterInputStream {

	private final long limit;
	private long count;

	LimitedInputStream(InputStream in, long limit) {
		super(in);
		this.limit = limit;
	}

	@Override
	public int read() throws IOException {
		int b = super.read();
		if (b >= 0) {
			add(1);
		}
		return b;
	}

	@Override
	public int read(byte[] buffer, int off, int len) throws IOException {
		int n = super.read(buffer, off, len);
		if (n > 0) {
			add(n);
		}
		return n;
	}

	@Override
	public long skip(long n) throws IOException {
		long skipped = super.skip(n);
		if (skipped > 0) {
			add(skipped);
		}
		return skipped;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	private void add(long n) {
		count += n;
		if (count > limit) {
			throw FileRejectedException.tooLarge(limit);
		}
	}

}
