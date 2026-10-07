package com.kpi.studentcouncil.crm.files.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.files.FileService;

@SpringBootTest(properties = { "crm.bootstrap.admin.login=Files.Admin",
		"crm.bootstrap.admin.password=Bootstrap-pass-42" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FilesIntegrationTests {

	private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	FileService fileService;

	@Autowired
	FilesProperties properties;

	@Value("${crm.files.root}")
	Path root;

	MockHttpSession session;

	@BeforeEach
	void signIn() throws Exception {
		jdbc.update("update users set must_change_password = false where login = 'files.admin'");
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
				.with(request -> {
					request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
					return request;
				}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"files.admin\",\"password\":\"Bootstrap-pass-42\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		session = (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockMultipartHttpServletRequestBuilder upload(String name, String declaredType, byte[] bytes) {
		return multipart("/api/v1/files").file(new MockMultipartFile("file", name, declaredType, bytes)).with(csrf())
				.session(session).header(HttpHeaders.ACCEPT_LANGUAGE, "en");
	}

	private UUID uploadOk(String name, String declaredType, byte[] bytes) throws Exception {
		MvcResult result = mvc.perform(upload(name, declaredType, bytes)).andExpect(status().isCreated()).andReturn();
		String body = result.getResponse().getContentAsString();
		return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1"));
	}

	@Test
	void anonymousCannotUploadOrDownload() throws Exception {
		mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
				.with(csrf())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/files/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	void uploadStoresBytesRowAndAuditAndPdfIsServedInline() throws Exception {
		UUID id = uploadOk("Report ü.pdf", "application/octet-stream", PDF);

		String key = jdbc.queryForObject("select storage_key from file_object where id = ?", String.class, id);
		assertThat(Files.readAllBytes(root.toAbsolutePath().normalize().resolve(key))).isEqualTo(PDF);
		assertThat(jdbc.queryForObject("select mime_type from file_object where id = ?", String.class, id))
				.isEqualTo("application/pdf");
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'FileObject' and entity_id = ? and action = 'CREATE'",
				Long.class, id)).isEqualTo(1);

		mvc.perform(get("/api/v1/files/" + id).session(session)).andExpect(status().isOk())
				.andExpect(content().bytes(PDF))
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
				.andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(PDF.length)))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline;")))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''Report%20%C3%BC.pdf")));
	}

	@Test
	void textIsServedAsAttachment() throws Exception {
		UUID id = uploadOk("notes.txt", "text/plain", "hello".getBytes());
		mvc.perform(get("/api/v1/files/" + id).session(session)).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void traversalInFileNameCannotEscapeTheStorageRoot() throws Exception {
		UUID id = uploadOk("../../../evil.pdf", "application/pdf", PDF);

		assertThat(jdbc.queryForObject("select file_name from file_object where id = ?", String.class, id))
				.isEqualTo("evil.pdf");
		String key = jdbc.queryForObject("select storage_key from file_object where id = ?", String.class, id);
		assertThat(key).doesNotContain("evil").doesNotContain("..");
		Path absoluteRoot = root.toAbsolutePath().normalize();
		assertThat(absoluteRoot.resolve(key).normalize()).startsWith(absoluteRoot).exists();
		assertThat(absoluteRoot.resolveSibling("evil.pdf")).doesNotExist();
		assertThat(absoluteRoot.getParent().resolve("evil.pdf")).doesNotExist();
	}

	@Test
	void disallowedContentIsRejectedWithProblemCodeEvenWithHarmlessNameAndType() throws Exception {
		byte[] exe = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 };
		mvc.perform(upload("photo.png", "image/png", exe)).andExpect(status().isUnsupportedMediaType())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
		mvc.perform(upload("page.txt", "text/plain", "<!DOCTYPE html><html><body>x</body></html>".getBytes()))
				.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
		mvc.perform(upload("i.svg", "image/svg+xml",
				"<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void emptyFileIsRejected() throws Exception {
		mvc.perform(upload("a.pdf", "application/pdf", new byte[0])).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("FILE_EMPTY"));
	}

	@Test
	void missingPartIsABadRequestProblem() throws Exception {
		mvc.perform(multipart("/api/v1/files").with(csrf()).session(session)).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void fileOverTheLimitIsRejectedAndLeavesNothingBehind() throws Exception {
		long before = jdbc.queryForObject("select count(*) from file_object", Long.class);
		byte[] big = new byte[(int) properties.maxSize().toBytes() + 1];
		System.arraycopy(PDF, 0, big, 0, PDF.length);

		mvc.perform(upload("big.pdf", "application/pdf", big)).andExpect(status().isContentTooLarge())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
				.andExpect(jsonPath("$.detail").value(containsString("20 ")));

		assertThat(jdbc.queryForObject("select count(*) from file_object", Long.class)).isEqualTo(before);
	}

	@Test
	void archivedFileIsNotDownloadableAndThereIsNoDeleteEndpoint() throws Exception {
		UUID id = uploadOk("a.pdf", "application/pdf", PDF);
		String key = jdbc.queryForObject("select storage_key from file_object where id = ?", String.class, id);

		mvc.perform(delete("/api/v1/files/" + id).with(csrf()).session(session))
				.andExpect(status().isMethodNotAllowed());

		fileService.archive(id);

		mvc.perform(get("/api/v1/files/" + id).session(session)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		assertThat(fileService.find(id)).isEmpty();
		assertThat(jdbc.queryForObject("select archived_at is not null from file_object where id = ?", Boolean.class, id))
				.isTrue();
		assertThat(root.toAbsolutePath().normalize().resolve(key)).exists();
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'FileObject' and entity_id = ? and action = 'ARCHIVE'",
				Long.class, id)).isEqualTo(1);
	}

}
