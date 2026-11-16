package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

/** AR-2: only http/https links with a host, at most 2048 characters. */
class ArtifactUrlValidatorTest {

	@ParameterizedTest
	@ValueSource(strings = { "https://example.org", "http://example.org/path?q=1#frag", "HTTPS://Example.ORG/A",
			"Http://example.org", "https://user@example.org:8443/x", "https://127.0.0.1/", "https://[::1]:8080/",
			"https://приклад.укр/сторінка", "https://example.org/%20encoded" })
	void acceptsHttpAndHttpsUrlsWithAHost(String url) {
		assertThat(ArtifactUrlValidator.isValid(url)).isTrue();
		assertThat(ArtifactUrlValidator.requireValid(url)).isEqualTo(url);
	}

	@ParameterizedTest
	@ValueSource(strings = { "javascript:alert(1)", "JavaScript:alert(1)", " javascript:alert(1)",
			"data:text/html;base64,PHNjcmlwdD4=", "ftp://example.org/file", "mailto:fake@example.org",
			"file:///etc/passwd", "vbscript:msgbox(1)", "//example.org/path", "/relative/path", "example.org",
			"https://", "https:///path", "http:example.org", "https://exa mple.org", "https://example.org/a b",
			"https://example.org/\ttab", "https://example.org/\nnewline", "https://example.org/\u0000",
			"https://:8080/", "https://@/", "https://.example.org", "jav\tascript:alert(1)" })
	void rejectsOtherSchemesRelativeUrlsMissingHostsAndInnerWhitespace(String url) {
		assertThat(ArtifactUrlValidator.isValid(url.strip())).isFalse();
		assertThatThrownBy(() -> ArtifactUrlValidator.requireValid(url)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("TASK_ARTIFACT_URL_INVALID"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t\n" })
	void rejectsMissingUrls(String url) {
		assertThatThrownBy(() -> ArtifactUrlValidator.requireValid(url)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.getCode()).isEqualTo("TASK_ARTIFACT_URL_INVALID"));
	}

	@Test
	void stripsSurroundingWhitespace() {
		assertThat(ArtifactUrlValidator.requireValid("  https://example.org/x \n")).isEqualTo("https://example.org/x");
	}

	@Test
	void acceptsExactly2048CharactersAndRejectsOneMore() {
		String prefix = "https://example.org/";
		String max = prefix + "a".repeat(ArtifactUrlValidator.MAX_LENGTH - prefix.length());
		assertThat(max).hasSize(2048);
		assertThat(ArtifactUrlValidator.requireValid(max)).isEqualTo(max);
		// surrounding whitespace does not count
		assertThat(ArtifactUrlValidator.requireValid("  " + max + "  ")).isEqualTo(max);

		assertThatThrownBy(() -> ArtifactUrlValidator.requireValid(max + "a"))
				.isInstanceOfSatisfying(DomainException.class, e -> {
					assertThat(e.getCode()).isEqualTo("TASK_ARTIFACT_URL_TOO_LONG");
					assertThat(e.getArgs()).containsExactly(2048);
				});
	}

}
