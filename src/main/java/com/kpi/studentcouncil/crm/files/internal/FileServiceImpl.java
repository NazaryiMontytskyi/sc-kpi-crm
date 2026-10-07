package com.kpi.studentcouncil.crm.files.internal;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileContent;
import com.kpi.studentcouncil.crm.files.FileInfo;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

@Service
class FileServiceImpl implements FileService {

	static final String ENTITY_TYPE = "FileObject";

	private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

	private final FileObjectRepository files;
	private final FileStorage storage;
	private final FilesProperties properties;
	private final ContentTypeResolver resolver = new ContentTypeResolver();
	private final Set<String> allowedTypes;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	FileServiceImpl(FileObjectRepository files, FileStorage storage, FilesProperties properties, AuditPublisher audit,
			ActorProvider actors, Clock clock) {
		this.files = files;
		this.storage = storage;
		this.properties = properties;
		this.allowedTypes = properties.allowedMimeTypes().stream().map(t -> t.trim().toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	@Override
	@Transactional
	public FileInfo upload(String fileName, InputStream content) {
		String name = FileNames.sanitize(fileName);
		byte[] header = readHeader(content);
		if (header.length == 0) {
			throw new BusinessRuleException("FILE_EMPTY");
		}
		String mimeType = resolver.resolve(header, name);
		if (!allowedTypes.contains(mimeType)) {
			throw FileRejectedException.typeNotAllowed(mimeType);
		}

		String key = StorageKeys.generate(clock);
		long size = write(key, new SequenceInputStream(new ByteArrayInputStream(header), content));
		removeBytesIfTransactionFails(key);

		FileObject saved = files.save(new FileObject(key, name, mimeType, size, actors.currentActorId()));
		audit.publish(AuditAction.CREATE, ENTITY_TYPE, saved.getId(), null, snapshot(saved));
		return toInfo(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<FileInfo> find(UUID id) {
		return files.findByIdAndArchivedAtIsNull(id).map(FileServiceImpl::toInfo);
	}

	@Override
	@Transactional(readOnly = true)
	public FileInfo require(UUID id) {
		return find(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

	@Override
	@Transactional(readOnly = true)
	public FileContent open(UUID id) {
		FileObject file = files.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
		try {
			return new FileContent(toInfo(file), storage.open(file.getStorageKey()));
		}
		catch (FileNotFoundException ex) {
			log.error("Bytes of file {} are missing in the storage", id);
			throw new NotFoundException(ENTITY_TYPE, id);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	@Transactional
	public void archive(UUID id) {
		FileObject file = files.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
		Map<String, Object> before = snapshot(file);
		file.archive(actors.currentActorId(), clock.instant());
		FileObject saved = files.save(file);
		audit.publish(AuditAction.ARCHIVE, ENTITY_TYPE, id, before, snapshot(saved));
	}

	private byte[] readHeader(InputStream content) {
		try {
			return content.readNBytes(ContentTypeResolver.SNIFF_BYTES);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private long write(String key, InputStream all) {
		try {
			return storage.store(key, new LimitedInputStream(all, properties.maxSize().toBytes()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Deletes the just-written bytes if the surrounding transaction does not commit. */
	private void removeBytesIfTransactionFails(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						storage.remove(key);
					}
					catch (IOException | RuntimeException ex) {
						log.warn("Could not remove orphaned bytes of a rolled-back upload", ex);
					}
				}
			}
		});
	}

	private static Map<String, Object> snapshot(FileObject file) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("fileName", file.getFileName());
		map.put("mimeType", file.getMimeType());
		map.put("size", file.getSize());
		map.put("uploadedBy", file.getUploadedBy());
		map.put("archivedAt", file.getArchivedAt() == null ? null : file.getArchivedAt().toString());
		return map;
	}

	private static FileInfo toInfo(FileObject file) {
		return new FileInfo(file.getId(), file.getFileName(), file.getMimeType(), file.getSize(), file.getUploadedBy(),
				file.getCreatedAt());
	}

}
