package com.kpi.studentcouncil.crm.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class PermissionLabelsParityTest {

	private static Properties load(String locale) throws Exception {
		Properties props = new Properties();
		try (InputStream in = PermissionLabelsParityTest.class.getResourceAsStream("/messages_" + locale + ".properties")) {
			props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return props;
	}

	@Test
	void everyPermissionAndGroupHasANonBlankLabelInBothLocales() throws Exception {
		for (String locale : new String[] { "uk", "en" }) {
			Properties props = load(locale);
			for (Permission p : Permission.values()) {
				assertThat(props.getProperty("permission." + p.key() + ".label")).as(locale + " " + p.key())
						.isNotBlank();
			}
			for (PermissionGroup g : PermissionGroup.values()) {
				assertThat(props.getProperty("permission.group." + g.code())).as(locale + " group " + g).isNotBlank();
			}
		}
	}

	@Test
	void everyPermissionBelongsToAGroupAndThereAre30() {
		assertThat(Permission.values()).hasSize(30).allMatch(p -> p.group() != null);
	}

}
