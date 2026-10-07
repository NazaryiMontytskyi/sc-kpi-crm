package com.kpi.studentcouncil.crm.identity.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** Outside dev/test the OpenAPI documents are not public (and the prod profile disables springdoc entirely). */
@SpringBootTest(properties = "crm.identity.public-api-docs=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiDocsRestrictedIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void apiDocsRequireSignInWhenNotPublic() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
	}

}
