package com.kpi.studentcouncil.crm.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.identity.AccountService;
import com.kpi.studentcouncil.crm.identity.NewAccount;

@Service
class AccountServiceImpl implements AccountService {

	private final UserAccountService accounts;

	private final ActorProvider actors;

	AccountServiceImpl(UserAccountService accounts, ActorProvider actors) {
		this.accounts = accounts;
		this.actors = actors;
	}

	@Override
	public NewAccount createAccount(String login, String locale) {
		return accounts.createWithTemporaryPassword(login, locale);
	}

	@Override
	public String resetPassword(UUID userId) {
		return accounts.resetToTemporaryPassword(userId);
	}

	@Override
	public void archiveAccount(UUID userId) {
		accounts.archiveAccount(userId, actors.currentActorId());
	}

	@Override
	public void restoreAccount(UUID userId) {
		accounts.restoreAccount(userId);
	}

	@Override
	public Optional<String> findLogin(UUID userId) {
		return accounts.findLogin(userId);
	}

}
