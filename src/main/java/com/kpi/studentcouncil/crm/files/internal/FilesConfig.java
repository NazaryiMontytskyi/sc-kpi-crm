package com.kpi.studentcouncil.crm.files.internal;

import jakarta.servlet.MultipartConfigElement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FilesProperties.class)
class FilesConfig {

	/** Headroom for the multipart envelope (boundaries, headers) on top of the largest allowed file. */
	private static final DataSize ENVELOPE = DataSize.ofKilobytes(64);

	@Bean
	@ConditionalOnMissingBean(FileStorage.class)
	FileStorage fileStorage(FilesProperties properties) {
		return new LocalFileStorage(properties.root());
	}

	/**
	 * Container-level multipart limits derived from {@code crm.files.max-size}, so there is a single setting. Larger
	 * uploads fail before reaching the controller and are mapped to the {@code FILE_TOO_LARGE} problem by
	 * {@link FilesExceptionHandler}. Replaces the {@code spring.servlet.multipart.*} based bean.
	 */
	@Bean
	MultipartConfigElement multipartConfigElement(FilesProperties properties) {
		long maxFile = properties.maxSize().toBytes();
		return new MultipartConfigElement("", maxFile, maxFile + ENVELOPE.toBytes(), (int) DataSize.ofKilobytes(64).toBytes());
	}

}
