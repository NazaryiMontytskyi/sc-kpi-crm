package com.kpi.studentcouncil.crm.members.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.seed.SeedScope;
import com.kpi.studentcouncil.crm.shared.seed.Seeder;

/**
 * Reference seed: the faculties and educational-scientific institutes of KPI (source: docs/FACULTIES_LIST.md). The
 * list can be adjusted afterwards by {@code settings.manage} holders; already existing codes are left untouched.
 */
@Component
class FacultySeeder implements Seeder {

	record Entry(String code, String nameUk, String nameEn) {
	}

	static final List<Entry> ENTRIES = List.of(
			new Entry("РТФ", "Радіотехнічний факультет", "Faculty of Radio Engineering"),
			new Entry("ФМФ", "Фізико-математичний факультет", "Faculty of Physics and Mathematics"),
			new Entry("ФІОТ", "Факультет інформатики та обчислювальної техніки", "Faculty of Informatics and Computer Science"),
			new Entry("ФАПІЕ", "Факультет автоматизації, промислової інженерії та екології", "Faculty of Automation, Industrial Engineering and Ecology"),
			new Entry("ФБМІ", "Факультет біомедичної інженерії", "Faculty of Biomedical Engineering"),
			new Entry("ФБТ", "Факультет біотехнології і біотехніки", "Faculty of Biotechnology and Biotechnics"),
			new Entry("ФЕА", "Факультет електроенерготехніки та автоматики", "Faculty of Electrical Engineering and Automation"),
			new Entry("ФЕЛ", "Факультет електроніки", "Faculty of Electronics"),
			new Entry("ФЛ", "Факультет лінгвістики", "Faculty of Linguistics"),
			new Entry("ФММ", "Факультет менеджменту та маркетингу", "Faculty of Management and Marketing"),
			new Entry("ФПСПМ", "Факультет програмних систем та прикладної математики", "Faculty of Software Systems and Applied Mathematics"),
			new Entry("ФРП", "Факультет робототехніки та приладобудування", "Faculty of Robotics and Instrument Engineering"),
			new Entry("ФСП", "Факультет соціології і права", "Faculty of Sociology and Law"),
			new Entry("ХТФ", "Хіміко-технологічний факультет", "Faculty of Chemical Technology"),
			new Entry("НН ІАТ", "Інститут аерокосмічних технологій", "Institute of Aerospace Technologies"),
			new Entry("НН ІАТЕ", "Інститут атомної та теплової енергетики", "Institute of Nuclear and Thermal Energy"),
			new Entry("НН ІЕЕ", "Інститут енергозбереження та енергоменеджменту", "Institute of Energy Saving and Energy Management"),
			new Entry("НН ІМЗ", "Інститут матеріалознавства та зварювання ім. Є.О. Патона", "Paton Institute of Materials Science and Welding"),
			new Entry("НН ІПСА", "Інститут прикладного системного аналізу", "Institute for Applied System Analysis"),
			new Entry("ІСЗЗІ", "Інститут спеціального зв'язку та захисту інформації", "Institute of Special Communication and Information Protection"),
			new Entry("НН ІТС", "Інститут телекомунікаційних систем", "Institute of Telecommunication Systems"),
			new Entry("НН ВПІ", "Видавничо-поліграфічний інститут", "Institute of Publishing and Printing"),
			new Entry("НН ММІ", "Механіко-машинобудівний інститут", "Institute of Mechanical Engineering"),
			new Entry("НН ФТІ", "Фізико-технічний інститут", "Institute of Physics and Technology"));

	static final int COUNT = ENTRIES.size();

	private final FacultyRepository faculties;
	private final FacultyService service;

	FacultySeeder(FacultyRepository faculties, FacultyService service) {
		this.faculties = faculties;
		this.service = service;
	}

	@Override
	public String id() {
		return "members.reference-faculties-kpi";
	}

	@Override
	public SeedScope scope() {
		return SeedScope.REFERENCE;
	}

	@Override
	public void run() {
		for (Entry entry : ENTRIES) {
			if (!exists(entry)) {
				service.create(entry.code(), entry.nameUk(), entry.nameEn());
			}
		}
	}

	/** True when the code or either name is already taken, so an existing (e.g. hand-made) row is never duplicated. */
	private boolean exists(Entry entry) {
		UUID none = new UUID(0, 0);
		return faculties.existsByCodeIgnoreCase(entry.code())
				|| faculties.existsByNameUkIgnoreCaseAndArchivedAtIsNullAndIdNot(entry.nameUk(), none)
				|| faculties.existsByNameEnIgnoreCaseAndArchivedAtIsNullAndIdNot(entry.nameEn(), none);
	}

}
