package com.gastointeligente.household;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTOs explícitos da família. Entidades JPA nunca são serializadas. Valores decimais trafegam como
 * string com ponto (por exemplo, {@code "10.00"}).
 */
public final class HouseholdDtos {

	private HouseholdDtos() {
	}

	// --- Configurações ---

	public record SettingsResponse(String currency, String timezone, int averageWindowMonths,
			String prudentMarginPercent, int cardPaymentOffsetMonths, long version) {

		static SettingsResponse of(FamilySettings s) {
			return new SettingsResponse(s.getCurrency(), s.getTimezone(), s.getAverageWindowMonths(),
					s.getPrudentMarginPercent().toPlainString(), s.getCardPaymentOffsetMonths(), s.getVersion());
		}

	}

	public record SettingsPatchRequest(@Size(max = 64) String timezone,
			@Min(1) @Max(12) Integer averageWindowMonths,
			@Pattern(regexp = "^\\d{1,3}(\\.\\d{1,2})?$",
					message = "Use número decimal com ponto e até duas casas, por exemplo 10.00.") String prudentMarginPercent,
			@Min(0) @Max(3) Integer cardPaymentOffsetMonths, @NotNull Long version) {
	}

	// --- Contas ---

	public record AccountResponse(UUID id, String name, AccountKind kind, AccountStatus status, Instant createdAt,
			long version) {

		static AccountResponse of(Account a) {
			return new AccountResponse(a.getId(), a.getName(), a.getKind(), a.getStatus(), a.getCreatedAt(),
					a.getVersion());
		}

	}

	public record AccountCreateRequest(@NotBlank @Size(max = 100) String name, @NotNull AccountKind kind) {
	}

	public record AccountPatchRequest(@Size(min = 1, max = 100) String name, AccountStatus status,
			@NotNull Long version) {
	}

	// --- Membros ---

	public record MemberResponse(UUID id, UUID userId, String email, String displayName, MembershipRole role,
			MembershipStatus status, Instant createdAt, long version) {

		static MemberResponse of(Membership m) {
			return new MemberResponse(m.getId(), m.getUser().getId(), m.getUser().getEmail(),
					m.getUser().getDisplayName(), m.getRole(), m.getStatus(), m.getCreatedAt(), m.getVersion());
		}

	}

	public record MemberCreateRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 100) String displayName, @NotNull MembershipRole role,
			@NotNull String initialPassword) {
	}

	public record MemberPatchRequest(MembershipRole role, MembershipStatus status, @NotNull Long version) {
	}

	public record PasswordResetRequest(@NotNull String newPassword) {
	}

}
