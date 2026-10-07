package com.kpi.studentcouncil.crm.files.internal;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;

/**
 * Determines the real content type of an upload from its leading bytes (Apache Tika magic detection), never from the
 * declared Content-Type. Container formats that magic bytes cannot tell apart (OOXML/ODF are ZIP, legacy Office is
 * OLE2) and plain text (CSV) are resolved to their final type by the file extension, but only when the bytes really
 * are that container, so an executable cannot be smuggled in under a harmless extension and vice versa.
 */
class ContentTypeResolver {

	/** Number of leading bytes inspected. */
	static final int SNIFF_BYTES = 8192;

	private static final String OOXML_CONTAINER = "application/x-tika-ooxml";
	private static final String OLE2_CONTAINER = "application/x-tika-msoffice";
	private static final String ZIP = "application/zip";
	private static final String TEXT = "text/plain";

	private static final Set<String> TEXT_EXTENSIONS = Set.of("", "txt", "text", "log", "md", "csv");

	private static final Map<String, String> OOXML = Map.of(
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

	private static final Map<String, String> ODF = Map.of(
			"odt", "application/vnd.oasis.opendocument.text",
			"ods", "application/vnd.oasis.opendocument.spreadsheet",
			"odp", "application/vnd.oasis.opendocument.presentation");

	private static final Map<String, String> OLE2 = Map.of(
			"doc", "application/msword",
			"xls", "application/vnd.ms-excel",
			"ppt", "application/vnd.ms-powerpoint");

	private final Tika tika = new Tika();

	/**
	 * Returns the effective content type of the file.
	 *
	 * @param header   leading bytes of the file (at most {@link #SNIFF_BYTES} are needed)
	 * @param fileName sanitized original file name
	 * @throws FileRejectedException {@code FILE_TYPE_NOT_ALLOWED} when the bytes and the name do not agree on a
	 *                               supported type (the allow-list itself is checked by the caller)
	 */
	String resolve(byte[] header, String fileName) {
		String sniffed = normalize(tika.detect(header));
		String extension = extensionOf(fileName);
		return switch (sniffed) {
			case TEXT -> {
				if (!TEXT_EXTENSIONS.contains(extension)) {
					throw FileRejectedException.typeNotAllowed(sniffed);
				}
				yield "csv".equals(extension) ? "text/csv" : TEXT;
			}
			case OOXML_CONTAINER -> require(OOXML.get(extension), sniffed);
			case OLE2_CONTAINER -> require(OLE2.get(extension), sniffed);
			case ZIP -> {
				if ("zip".equals(extension)) {
					yield ZIP;
				}
				yield require(ODF.get(extension), sniffed);
			}
			default -> sniffed;
		};
	}

	private static String require(String resolved, String sniffed) {
		if (resolved == null) {
			throw FileRejectedException.typeNotAllowed(sniffed);
		}
		return resolved;
	}

	private static String normalize(String type) {
		int semicolon = type.indexOf(';');
		return (semicolon < 0 ? type : type.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
	}

	static String extensionOf(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

}
