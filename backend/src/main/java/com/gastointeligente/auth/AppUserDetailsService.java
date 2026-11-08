package com.gastointeligente.auth;

import com.gastointeligente.household.Membership;
import com.gastointeligente.household.MembershipRepository;
import com.gastointeligente.household.MembershipStatus;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o usuário para o {@code DaoAuthenticationProvider} do Spring Security. Usuários sem
 * associação ativa à família são tratados como desativados; a resposta ao cliente é sempre a mesma
 * mensagem genérica.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final MembershipRepository memberships;

	public AppUserDetailsService(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		String email = AppUser.normalizeEmail(username);
		Membership membership = this.memberships.findByUserEmail(email)
			.orElseThrow(() -> new UsernameNotFoundException("not found"));
		AppUser user = membership.getUser();
		boolean enabled = user.getStatus() == UserStatus.ACTIVE
				&& membership.getStatus() == MembershipStatus.ACTIVE;
		return new AppUserPrincipal(user.getId(), membership.getFamilyId(), membership.getId(), user.getEmail(),
				user.getDisplayName(), membership.getRole(), user.getSecurityStamp(), enabled,
				user.getPasswordHash());
	}

}
