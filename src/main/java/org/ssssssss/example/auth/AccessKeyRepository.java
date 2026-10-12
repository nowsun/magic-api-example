package org.ssssssss.example.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccessKeyRepository {

	private static final String FIND_ACTIVE_SQL =
			"select tenant_id, access_key_secret_hash from magic_api_access " +
			"where access_key_id = ? and status = 1 " +
			"and (expires_at is null or expires_at > current_timestamp)";

	private final JdbcTemplate jdbcTemplate;

	public AccessKeyRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<AccessKeyRecord> findActive(String accessKeyId) {
		List<AccessKeyRecord> results = jdbcTemplate.query(FIND_ACTIVE_SQL,
				(resultSet, rowNumber) -> new AccessKeyRecord(
						resultSet.getLong("tenant_id"),
						resultSet.getString("access_key_secret_hash")),
				accessKeyId);
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	public static final class AccessKeyRecord {
		private final long tenantId;
		private final String secretHash;

		public AccessKeyRecord(long tenantId, String secretHash) {
			this.tenantId = tenantId;
			this.secretHash = secretHash;
		}

		public long getTenantId() {
			return tenantId;
		}

		public String getSecretHash() {
			return secretHash;
		}
	}
}
