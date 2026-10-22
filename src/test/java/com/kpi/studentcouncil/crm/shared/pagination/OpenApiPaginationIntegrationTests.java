package com.kpi.studentcouncil.crm.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** The OpenAPI contract must declare pagination as flat query parameters, matching how Spring binds them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiPaginationIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void paginatedListsDeclareFlatPageSizeSortParameters() throws Exception {
		JsonNode doc = apiDocs();
		for (String path : List.of("/api/v1/members", "/api/v1/faculties", "/api/v1/activity-levels")) {
			JsonNode params = doc.at("/paths").path(path).path("get").path("parameters");
			assertThat(params.isArray()).as(path).isTrue();
			assertThat(param(params, "page").at("/schema/type").asString()).as(path + " page").isEqualTo("integer");
			assertThat(param(params, "size").at("/schema/type").asString()).as(path + " size").isEqualTo("integer");
			assertThat(param(params, "sort").at("/schema/type").asString()).as(path + " sort").isEqualTo("string");
			assertThat(param(params, "page").path("in").asString()).isEqualTo("query");
		}
	}

	@Test
	void noOperationDeclaresAnObjectPageParameter() throws Exception {
		JsonNode doc = apiDocs();
		List<String> offenders = new ArrayList<>();
		doc.at("/paths").properties().forEach(pathEntry -> pathEntry.getValue().properties().forEach(opEntry -> {
			for (JsonNode p : opEntry.getValue().path("parameters")) {
				if ("page".equals(p.path("name").asString())
						&& (p.path("schema").has("$ref") || "object".equals(p.at("/schema/type").asString()))) {
					offenders.add(opEntry.getKey() + " " + pathEntry.getKey());
				}
			}
		}));
		assertThat(offenders).isEmpty();
	}

	private JsonNode apiDocs() throws Exception {
		String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		return new ObjectMapper().readTree(body);
	}

	private static JsonNode param(JsonNode params, String name) {
		for (JsonNode p : params) {
			if (name.equals(p.path("name").asString())) {
				return p;
			}
		}
		return tools.jackson.databind.node.MissingNode.getInstance();
	}

}
