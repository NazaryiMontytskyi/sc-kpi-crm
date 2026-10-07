package com.kpi.studentcouncil.crm.files.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

class FilesUnitTests {

	private final ContentTypeResolver resolver = new ContentTypeResolver();

	// ---- storage keys ----

	@Test
	void generatedKeysAreRandomValidAndDatePartitioned() {
		Clock clock = Clock.fixed(Instant.parse("2026-03-05T10:00:00Z"), ZoneOffset.UTC);
		String a = StorageKeys.generate(clock);
		String b = StorageKeys.generate(clock);
		assertThat(a).startsWith("2026/03/").isNotEqualTo(b);
		assertThat(StorageKeys.isValid(a)).isTrue();
	}

	@Test
	void hostileKeysAreInvalid() {
		for (String key : new String[] { null, "", "../x", "2026/03/../../etc/passwd", "/etc/passwd", "..\\x",
				"2026/03/abc", "2026/03/00000000-0000-0000-0000-000000000000/../a" }) {
			assertThat(StorageKeys.isValid(key)).as(String.valueOf(key)).isFalse();
		}
	}

	// ---- local storage ----

	@Test
	void localStorageRoundTripsAndRejectsKeysOutsideTheRoot(@TempDir Path dir) throws IOException {
		LocalFileStorage storage = new LocalFileStorage(dir.resolve("root"));
		String key = StorageKeys.generate(Clock.systemUTC());
		assertThat(storage.store(key, new ByteArrayInputStream("abc".getBytes()))).isEqualTo(3);
		assertThat(storage.open(key).readAllBytes()).isEqualTo("abc".getBytes());
		storage.remove(key);
		assertThatThrownBy(() -> storage.open(key)).isInstanceOf(FileNotFoundException.class);

		assertThatThrownBy(() -> storage.store("../../evil", new ByteArrayInputStream(new byte[1])))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.open("../../evil")).isInstanceOf(IllegalArgumentException.class);
		assertThat(dir.resolve("evil")).doesNotExist();
	}

	@Test
	void failedStoreLeavesNothingBehind(@TempDir Path dir) throws IOException {
		LocalFileStorage storage = new LocalFileStorage(dir);
		String key = StorageKeys.generate(Clock.systemUTC());
		assertThatThrownBy(() -> storage.store(key, new LimitedInputStream(new ByteArrayInputStream(new byte[100]), 10)))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> storage.open(key)).isInstanceOf(FileNotFoundException.class);
		try (Stream<Path> tmp = Files.list(dir.resolve(".tmp"))) {
			assertThat(tmp).isEmpty();
		}
	}

	// ---- limit ----

	@Test
	void limitedStreamAllowsExactlyTheLimit() throws IOException {
		assertThat(new LimitedInputStream(new ByteArrayInputStream(new byte[10]), 10).readAllBytes()).hasSize(10);
		assertThatThrownBy(() -> new LimitedInputStream(new ByteArrayInputStream(new byte[11]), 10).readAllBytes())
				.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("FILE_TOO_LARGE"));
	}

	// ---- names ----

	@Test
	void fileNamesAreSanitized() {
		assertThat(FileNames.sanitize("../../etc/passwd")).isEqualTo("passwd");
		assertThat(FileNames.sanitize("..\\..\\win\\x.txt")).isEqualTo("x.txt");
		assertThat(FileNames.sanitize("a\u0000b\r\n.pdf")).isEqualTo("ab.pdf");
		assertThat(FileNames.sanitize("evil‮fdp.exe")).isEqualTo("evilfdp.exe");
		assertThat(FileNames.sanitize("..")).isEqualTo("file");
		assertThat(FileNames.sanitize(null)).isEqualTo("file");
		assertThat(FileNames.sanitize("x".repeat(400) + ".pdf")).hasSize(255).endsWith(".pdf");
	}

	@Test
	void contentDispositionEncodesNamesSafely() {
		assertThat(ContentDispositions.build(true, "report.pdf"))
				.isEqualTo("inline; filename=\"report.pdf\"; filename*=UTF-8''report.pdf");
		String value = ContentDispositions.build(false, "звіт \"1\";x.txt");
		assertThat(value).startsWith("attachment; filename=\"____ _1__x.txt\"")
				.contains("filename*=UTF-8''%D0%B7%D0%B2%D1%96%D1%82%20%221%22%3Bx.txt");
		assertThat(ContentDispositions.isInline("application/pdf")).isTrue();
		assertThat(ContentDispositions.isInline("image/png")).isTrue();
		assertThat(ContentDispositions.isInline("image/svg+xml")).isFalse();
		assertThat(ContentDispositions.isInline("text/plain")).isFalse();
	}

	// ---- content sniffing ----

	private static byte[] zip(String... entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(out)) {
			for (int i = 0; i < entries.length; i += 2) {
				z.putNextEntry(new ZipEntry(entries[i]));
				z.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
				z.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static byte[] ole() {
		byte[] b = new byte[2048];
		byte[] sig = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };
		System.arraycopy(sig, 0, b, 0, 8);
		return b;
	}

	@Test
	void allowedTypesAreResolvedFromBytesAndExtension() throws IOException {
		assertThat(resolver.resolve("%PDF-1.4\n".getBytes(), "a.pdf")).isEqualTo("application/pdf");
		assertThat(resolver.resolve("%PDF-1.4\n".getBytes(), "no-extension")).isEqualTo("application/pdf");
		assertThat(resolver.resolve("a,b\n1,2\n".getBytes(), "t.csv")).isEqualTo("text/csv");
		assertThat(resolver.resolve("hello".getBytes(), "t.txt")).isEqualTo("text/plain");
		assertThat(resolver.resolve(zip("a.txt", "x"), "a.zip")).isEqualTo("application/zip");
		assertThat(resolver.resolve(zip("[Content_Types].xml", "<Types/>", "word/document.xml", "<w/>"), "a.docx"))
				.isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		assertThat(resolver.resolve(zip("[Content_Types].xml", "<Types/>", "ppt/presentation.xml", "<w/>"), "a.pptx"))
				.isEqualTo("application/vnd.openxmlformats-officedocument.presentationml.presentation");
		assertThat(resolver.resolve(zip("mimetype", "application/vnd.oasis.opendocument.text", "content.xml", "<w/>"),
				"a.odt")).isEqualTo("application/vnd.oasis.opendocument.text");
		assertThat(resolver.resolve(ole(), "a.xls")).isEqualTo("application/vnd.ms-excel");
	}

	@Test
	void dangerousOrMismatchedContentIsRejectedDespiteExtension() throws IOException {
		byte[] exe = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 };
		assertRejected(exe, "photo.png");
		assertRejected(exe, "doc.pdf");
		assertRejected("<!DOCTYPE html><html><body>x</body></html>".getBytes(), "page.html");
		assertRejected("<!DOCTYPE html><html><body>x</body></html>".getBytes(), "notes.txt");
		assertRejected("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(), "i.svg");
		assertRejected("#!/bin/sh\necho hi\n".getBytes(), "run.sh");
		assertRejected("alert(1)".getBytes(), "x.js");
		assertRejected(new byte[] { 1, 2, 3, 0, 5, 6, (byte) 200, (byte) 201 }, "blob.pdf");
		assertRejected(zip("META-INF/MANIFEST.MF", "x"), "app.jar");
		assertRejected(zip("a.txt", "x"), "a.docx");
		assertRejected(ole(), "a.msi");
	}

	private static final java.util.Set<String> ALLOWED = java.util.Set.of("application/pdf", "image/png", "image/jpeg",
			"image/gif", "image/webp", "text/plain", "text/csv", "application/zip");

	private void assertRejected(byte[] bytes, String name) {
		boolean rejected;
		try {
			rejected = !ALLOWED.contains(resolver.resolve(bytes, name));
		}
		catch (DomainException ex) {
			rejected = "FILE_TYPE_NOT_ALLOWED".equals(ex.getCode());
		}
		assertThat(rejected).as(name).isTrue();
	}

}
