package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** End-to-end over a real servlet container: cookie flags and the SPA CSRF cookie/header round trip. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SessionCookieIntegrationTests {

	static final String PASSWORD = "Sup3r-secret-pw";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder encoder;

	final HttpClient client = HttpClient.newHttpClient();

	boolean expectSecure() {
		return false;
	}

	HttpResponse<String> send(String method, String path, String body, String cookies, String csrf) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
		if (cookies != null) {
			builder.header("Cookie", cookies);
		}
		if (csrf != null) {
			builder.header("X-XSRF-TOKEN", csrf);
		}
		return client.send(builder.build(), BodyHandlers.ofString());
	}

	static String cookieHeader(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith(name + "="))
				.reduce((first, second) -> second).orElse(null);
	}

	static String cookieValue(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';') < 0 ? setCookie.length() : setCookie.indexOf(';'));
	}

	@Test
	void sessionCookieIsHttpOnlyAndCsrfRoundTripWorks() throws Exception {
		String login = "cookie-" + UUID.randomUUID().toString().substring(0, 8);
		users.save(User.create(login, encoder.encode(PASSWORD), "uk", false));

		// 1. anonymous call: 401 problem, but the XSRF-TOKEN cookie is handed out (readable by the SPA)
		HttpResponse<String> anonymous = send("GET", "/api/v1/auth/me", null, null, null);
		assertThat(anonymous.statusCode()).isEqualTo(401);
		String xsrf = cookieHeader(anonymous, "XSRF-TOKEN");
		assertThat(xsrf).isNotNull().doesNotContain("HttpOnly");
		String token = cookieValue(xsrf);

		// 2. sign-in without header is rejected, with header it works
		String body = "{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}";
		assertThat(send("POST", "/api/v1/auth/login", body, "XSRF-TOKEN=" + token, null).statusCode()).isEqualTo(403);
		HttpResponse<String> signedIn = send("POST", "/api/v1/auth/login", body, "XSRF-TOKEN=" + token, token);
		assertThat(signedIn.statusCode()).isEqualTo(200);

		String session = cookieHeader(signedIn, "CRMSESSION");
		assertThat(session).isNotNull().contains("HttpOnly").contains("SameSite=Lax");
		if (expectSecure()) {
			assertThat(session).contains("Secure");
			assertThat(cookieHeader(signedIn, "XSRF-TOKEN")).contains("Secure");
		}
		else {
			assertThat(session).doesNotContain("Secure");
		}
		String newToken = cookieValue(cookieHeader(signedIn, "XSRF-TOKEN"));
		assertThat(newToken).isNotEqualTo(token); // rotated on sign-in

		// 3. session works, logout needs the token
		String cookies = "CRMSESSION=" + cookieValue(session) + "; XSRF-TOKEN=" + newToken;
		assertThat(send("GET", "/api/v1/auth/me", null, cookies, null).statusCode()).isEqualTo(200);
		assertThat(send("POST", "/api/v1/auth/logout", null, cookies, null).statusCode()).isEqualTo(403);
		HttpResponse<String> out = send("POST", "/api/v1/auth/logout", null, cookies, newToken);
		assertThat(out.statusCode()).as(out.body()).isEqualTo(204);
		assertThat(send("GET", "/api/v1/auth/me", null, cookies, null).statusCode()).isEqualTo(401);
	}

	@Test
	void unknownCookieNamesAreIgnored() throws Exception {
		assertThat(List.of(send("GET", "/api/v1/auth/me", null, "JSESSIONID=abc", null).statusCode())).containsExactly(401);
	}

}
