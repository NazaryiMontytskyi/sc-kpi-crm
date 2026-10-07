package com.kpi.studentcouncil.crm.files.internal;

import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository;

/** No delete methods by design (see {@link ArchivableRepository}). */
interface FileObjectRepository extends ArchivableRepository<FileObject, UUID> {
}
