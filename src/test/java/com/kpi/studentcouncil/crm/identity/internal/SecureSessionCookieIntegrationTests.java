package com.kpi.studentcouncil.crm.identity.internal;

import org.springframework.boot.test.context.SpringBootTest;

/** Same round trip with {@code server.servlet.session.cookie.secure=true} (what the prod profile sets). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "server.servlet.session.cookie.secure=true")
class SecureSessionCookieIntegrationTests extends SessionCookieIntegrationTests {

	@Override
	boolean expectSecure() {
		return true;
	}

}
