package com.gastointeligente.household;

import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiException;

/** Concorrência otimista: a versão enviada pelo cliente deve ser a versão atual do registro. */
final class Versions {

	private Versions() {
	}

	static void require(Long current, Long expected) {
		if (current == null || !current.equals(expected)) {
			throw new ApiException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

}
