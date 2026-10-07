package com.kpi.studentcouncil.crm.files;

import java.io.InputStream;

/** A file's metadata together with its bytes. The caller must close {@link #stream()}. */
public record FileContent(FileInfo info, InputStream stream) {
}
