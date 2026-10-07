package com.kpi.studentcouncil.crm.files.internal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stores files on the local disk below a configured root (a Docker volume in production). Every key is validated
 * and the resolved path is checked to stay inside the root, so no key can escape it. Bytes are written to a
 * temporary file first and moved into place, so a half-written file never appears under its key.
 */
class LocalFileStorage implements FileStorage {

	private static final String TMP_DIR = ".tmp";

	private final Path root;

	LocalFileStorage(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	@Override
	public long store(String key, InputStream content) throws IOException {
		Path target = resolve(key);
		Files.createDirectories(target.getParent());
		Path tmpDir = root.resolve(TMP_DIR);
		Files.createDirectories(tmpDir);
		Path tmp = Files.createTempFile(tmpDir, "upload-", ".part");
		try {
			long written = Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, target);
			}
			return written;
		}
		finally {
			Files.deleteIfExists(tmp);
		}
	}

	@Override
	public InputStream open(String key) throws IOException {
		try {
			return Files.newInputStream(resolve(key));
		}
		catch (NoSuchFileException ex) {
			throw new FileNotFoundException(key);
		}
	}

	@Override
	public void remove(String key) throws IOException {
		Files.deleteIfExists(resolve(key));
	}

	private Path resolve(String key) {
		if (!StorageKeys.isValid(key)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Storage key escapes the storage root");
		}
		return path;
	}

}
