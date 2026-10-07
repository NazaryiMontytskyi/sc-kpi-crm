package com.kpi.studentcouncil.crm.files.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/**
 * Real embedded container: the multipart limit derived from {@code crm.files.max-size} must surface as the
 * FILE_TOO_LARGE problem, not as a raw container error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"crm.files.max-size=100KB", "crm.bootstrap.admin.login=Limit.Admin",
		"crm.bootstrap.admin.password=Bootstrap-pass-42" })
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FilesContainerLimitIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${crm.files.root}")
	String root;

	@Test
	void oversizedMultipartIsReportedAsFileTooLargeProblem() throws Exception {
		jdbc.update("update users set must_change_password = false where login = 'limit.admin'");
		CookieManager cookies = new CookieManager();
		HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
		String base = "http://localhost:" + port;

		client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/me")).build(),
				HttpResponse.BodyHandlers.discarding());
		String csrf = xsrf(cookies);
		HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
				.header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf)
				.POST(HttpRequest.BodyPublishers.ofString("{\"login\":\"limit.admin\",\"password\":\"Bootstrap-pass-42\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(login.statusCode()).isEqualTo(200);
		csrf = xsrf(cookies);

		HttpResponse<String> tooBig = send(client, base, csrf, 150 * 1024);
		assertThat(tooBig.statusCode()).isEqualTo(413);
		assertThat(tooBig.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
		assertThat(tooBig.body()).contains("\"code\":\"FILE_TOO_LARGE\"");

		HttpResponse<String> fine = send(client, base, csrf, 50 * 1024);
		assertThat(fine.statusCode()).isEqualTo(201);
	}

	private static String xsrf(CookieManager cookies) {
		return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
				.findFirst().orElseThrow().getValue();
	}

	private static HttpResponse<String> send(HttpClient client, String base, String csrf, int size) throws Exception {
		String boundary = "----crmtest" + System.nanoTime();
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.pdf\"\r\n"
				+ "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
		byte[] bytes = new byte[size];
		byte[] magic = "%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(magic, 0, bytes, 0, magic.length);
		body.write(bytes);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
		return client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/files"))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary).header("X-XSRF-TOKEN", csrf)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
				HttpResponse.BodyHandlers.ofString());
	}

}
