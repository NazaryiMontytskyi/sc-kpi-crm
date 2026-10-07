# ADR-0005: File storage and content-type detection

Status: accepted (INC-009)

## Context
Files are stored inside the system (CONTEXT.md, files row). Uploads are untrusted: the declared Content-Type and the
file name can be forged, so the type must be derived from the bytes. Executables, scripts, HTML and SVG must never be
accepted (Q8).

## Decisions
- **Storage abstraction**: `FileStorage` (store / open / remove by opaque key). `LocalFileStorage` writes below
  `crm.files.root` (env `CRM_FILES_ROOT`, a Docker volume in deployment) via a temp file and atomic move. An S3/MinIO
  implementation can replace the bean later (`@ConditionalOnMissingBean`).
- **Keys** are `yyyy/MM/<random uuid>`; the original name is display metadata only and never part of a path. Keys are
  re-validated by pattern and by a `startsWith(root)` check on every access.
- **Content sniffing**: `org.apache.tika:tika-core` 3.3.2 (Apache-2.0, actively maintained, no transitive parsers, one
  small jar). Only magic-byte detection of the first 8 KiB is used. Tika 4.x was available but 3.x is the conservative,
  well-known line; upgrade is a one-line change. Hand-written magic tables were rejected as error-prone.
- **Container formats**: Tika core cannot tell OOXML/ODF (ZIP) or legacy Office (OLE2) apart, so the final type is
  resolved by extension *only if* the bytes are the matching container (e.g. `.docx` requires an OOXML container,
  `.doc/.xls/.ppt` an OLE2 file, `.odt/.ods/.odp` or `.zip` a ZIP). Plain text is accepted only with a text extension
  (`txt`, `csv`, `md`, `log`, none), `csv` maps to `text/csv`. Anything else (exe, scripts, HTML, SVG, jar renamed,
  random binary) yields `FILE_TYPE_NOT_ALLOWED` (415). The result is then checked against the configurable allow-list
  `crm.files.allowed-mime-types`. Residual risk: a ZIP/OLE2 file may contain harmful content; it is only ever served as
  an attachment with `nosniff`. No antivirus (out of scope).
- **Limits**: `crm.files.max-size` (default 20 MB). The service counts bytes while streaming (`FILE_TOO_LARGE`, 413) and
  the multipart container limits are derived from the same property by a `MultipartConfigElement` bean, with
  `FilesExceptionHandler` mapping the container's `MaxUploadSizeExceededException` to the same problem code.
- **Download**: `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-cache`, inline only for PDF and
  png/jpeg/gif/webp, attachment otherwise; the name is sent as ASCII fallback plus RFC 5987 `filename*`.
- **Access**: upload/download need authentication only (CONTEXT.md 5.2 has no file permission); modules that expose
  files apply their own permission checks. Files are archived through `FileService.archive`, there is no delete endpoint.
- **Property prefix** is `crm.files.*` to match `crm.identity.*`.

## Consequences
- Embedding a PDF in an iframe needs `X-Frame-Options: SAMEORIGIN` (Spring Security default is `DENY` and overrides the
  response header); this is a change in the identity security config, left to a separate decision.
- Uploads above the Tomcat swallow limit (2 MB past the limit) may see a connection reset instead of the problem body.
