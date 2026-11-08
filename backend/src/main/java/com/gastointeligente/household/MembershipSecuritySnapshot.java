package com.gastointeligente.household;

import java.util.UUID;

import com.gastointeligente.auth.UserStatus;

public record MembershipSecuritySnapshot(MembershipRole role, MembershipStatus membershipStatus,
		UserStatus userStatus, UUID securityStamp, String displayName) {

	public boolean isActive() {
		return this.membershipStatus == MembershipStatus.ACTIVE && this.userStatus == UserStatus.ACTIVE;
	}

}
