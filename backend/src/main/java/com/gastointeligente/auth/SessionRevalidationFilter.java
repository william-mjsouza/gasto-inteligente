package com.gastointeligente.auth;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.gastointeligente.household.MembershipRepository;
import com.gastointeligente.household.MembershipSecuritySnapshot;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revalida a sessão em toda requisição autenticada contra o estado atual no banco:
 * <ul>
 * <li>associação ou usuário desativado, ou senha redefinida (carimbo de segurança diferente):
 * a sessão é invalidada e a requisição segue como anônima (a API responde 401);</li>
 * <li>papel alterado: a autenticação da sessão é atualizada com as novas permissões.</li>
 * </ul>
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

	private final MembershipRepository memberships;

	private final SecurityContextRepository contextRepository;

	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	public SessionRevalidationFilter(MembershipRepository memberships, SecurityContextRepository contextRepository) {
		this.memberships = memberships;
		this.contextRepository = contextRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = this.holder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
			Optional<MembershipSecuritySnapshot> snapshot = this.memberships
				.findSecuritySnapshot(principal.membershipId());
			if (snapshot.isEmpty() || !snapshot.get().isActive()
					|| !snapshot.get().securityStamp().equals(principal.securityStamp())) {
				revoke(request);
			}
			else if (snapshot.get().role() != principal.role()
					|| !snapshot.get().displayName().equals(principal.displayName())) {
				refresh(request, response, principal, snapshot.get());
			}
		}
		chain.doFilter(request, response);
	}

	private void revoke(HttpServletRequest request) {
		this.holder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

	private void refresh(HttpServletRequest request, HttpServletResponse response, AppUserPrincipal principal,
			MembershipSecuritySnapshot snapshot) {
		AppUserPrincipal updated = principal.withRoleAndName(snapshot.role(), snapshot.displayName());
		UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.authenticated(updated, null,
				AppUserPrincipal.authoritiesFor(snapshot.role()));
		SecurityContext context = this.holder.createEmptyContext();
		context.setAuthentication(token);
		this.holder.setContext(context);
		this.contextRepository.saveContext(context, request, response);
	}

}
