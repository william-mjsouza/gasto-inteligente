package com.gastointeligente.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.gastointeligente.household.MembershipRole;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuário autenticado guardado na sessão. O {@code familyId} usado em toda autorização vem daqui,
 * nunca de parâmetros enviados pelo cliente.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID userId;

	private final UUID familyId;

	private final UUID membershipId;

	private final String email;

	private final String displayName;

	private final MembershipRole role;

	private final UUID securityStamp;

	private final boolean enabled;

	private String passwordHash;

	public AppUserPrincipal(UUID userId, UUID familyId, UUID membershipId, String email, String displayName,
			MembershipRole role, UUID securityStamp, boolean enabled, String passwordHash) {
		this.userId = userId;
		this.familyId = familyId;
		this.membershipId = membershipId;
		this.email = email;
		this.displayName = displayName;
		this.role = role;
		this.securityStamp = securityStamp;
		this.enabled = enabled;
		this.passwordHash = passwordHash;
	}

	public AppUserPrincipal withRoleAndName(MembershipRole newRole, String newDisplayName) {
		return new AppUserPrincipal(this.userId, this.familyId, this.membershipId, this.email, newDisplayName,
				newRole, this.securityStamp, this.enabled, null);
	}

	public AppUserPrincipal withSecurityStamp(UUID newStamp) {
		return new AppUserPrincipal(this.userId, this.familyId, this.membershipId, this.email, this.displayName,
				this.role, newStamp, this.enabled, null);
	}

	public static List<GrantedAuthority> authoritiesFor(MembershipRole role) {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authoritiesFor(this.role);
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

	public UUID userId() {
		return this.userId;
	}

	public UUID familyId() {
		return this.familyId;
	}

	public UUID membershipId() {
		return this.membershipId;
	}

	public String email() {
		return this.email;
	}

	public String displayName() {
		return this.displayName;
	}

	public MembershipRole role() {
		return this.role;
	}

	public UUID securityStamp() {
		return this.securityStamp;
	}

	public boolean isAdmin() {
		return this.role == MembershipRole.ADMIN;
	}

	@Override
	public String toString() {
		// Sem e-mail nem hash: o principal pode aparecer em logs de framework.
		return "AppUserPrincipal[userId=" + this.userId + ", familyId=" + this.familyId + ", role=" + this.role
				+ "]";
	}

}
