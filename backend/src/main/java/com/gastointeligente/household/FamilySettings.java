package com.gastointeligente.household;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Parâmetros da família usados pelas etapas seguintes (média, margem e política de cartão).
 * Moeda é sempre BRL no MVP.
 */
@Entity
@Table(name = "family_settings")
public class FamilySettings {

	public static final String CURRENCY = "BRL";

	public static final String DEFAULT_TIMEZONE = "America/Fortaleza";

	public static final int DEFAULT_AVERAGE_WINDOW_MONTHS = 3;

	public static final BigDecimal DEFAULT_PRUDENT_MARGIN_PERCENT = new BigDecimal("10.00");

	/** Novas compras no cartão são pagas, por padrão, na fatura do mês seguinte (seção 11.3). */
	public static final int DEFAULT_CARD_PAYMENT_OFFSET_MONTHS = 1;

	@Id
	@Column(name = "family_id")
	private UUID familyId;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false)
	private String timezone;

	@Column(name = "average_window_months", nullable = false)
	private short averageWindowMonths;

	@Column(name = "prudent_margin_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal prudentMarginPercent;

	@Column(name = "card_payment_offset_months", nullable = false)
	private short cardPaymentOffsetMonths;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected FamilySettings() {
	}

	public static FamilySettings defaults(UUID familyId, Instant now) {
		FamilySettings settings = new FamilySettings();
		settings.familyId = familyId;
		settings.currency = CURRENCY;
		settings.timezone = DEFAULT_TIMEZONE;
		settings.averageWindowMonths = DEFAULT_AVERAGE_WINDOW_MONTHS;
		settings.prudentMarginPercent = DEFAULT_PRUDENT_MARGIN_PERCENT;
		settings.cardPaymentOffsetMonths = DEFAULT_CARD_PAYMENT_OFFSET_MONTHS;
		settings.updatedAt = now;
		return settings;
	}

	public void update(String timezone, Integer averageWindowMonths, BigDecimal prudentMarginPercent,
			Integer cardPaymentOffsetMonths, Instant now) {
		if (timezone != null) {
			this.timezone = timezone;
		}
		if (averageWindowMonths != null) {
			this.averageWindowMonths = averageWindowMonths.shortValue();
		}
		if (prudentMarginPercent != null) {
			this.prudentMarginPercent = prudentMarginPercent;
		}
		if (cardPaymentOffsetMonths != null) {
			this.cardPaymentOffsetMonths = cardPaymentOffsetMonths.shortValue();
		}
		this.updatedAt = now;
	}

	public UUID getFamilyId() {
		return this.familyId;
	}

	public String getCurrency() {
		return this.currency;
	}

	public String getTimezone() {
		return this.timezone;
	}

	public int getAverageWindowMonths() {
		return this.averageWindowMonths;
	}

	public BigDecimal getPrudentMarginPercent() {
		return this.prudentMarginPercent;
	}

	public int getCardPaymentOffsetMonths() {
		return this.cardPaymentOffsetMonths;
	}

	public Long getVersion() {
		return this.version;
	}

}
