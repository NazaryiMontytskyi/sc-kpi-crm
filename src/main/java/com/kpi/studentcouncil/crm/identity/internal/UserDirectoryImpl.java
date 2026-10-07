package com.kpi.studentcouncil.crm.identity.internal;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.identity.UserDirectory;

@Service
class UserDirectoryImpl implements UserDirectory {

	private final UserRepository users;

	UserDirectoryImpl(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean exists(UUID userId) {
		return users.findById(userId).isPresent();
	}

}
