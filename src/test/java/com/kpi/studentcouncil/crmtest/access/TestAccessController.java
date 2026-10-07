package com.kpi.studentcouncil.crmtest.access;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Test-only endpoints guarded by permission keys. */
@ApiV1
@RequestMapping("/test-access")
public class TestAccessController {

	@GetMapping("/activity")
	@PreAuthorize("hasAuthority('members.activity.set')")
	public String activity() {
		return "ok";
	}

	@GetMapping("/audit")
	@PreAuthorize("hasAuthority('audit.view')")
	public String audit() {
		return "ok";
	}

}
