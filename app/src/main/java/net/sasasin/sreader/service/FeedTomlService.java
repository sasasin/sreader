package net.sasasin.sreader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.UnsubscribeReason;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedTomlService {

	private final FeedUrlRepository feedUrlRepository;
	private final Clock clock;

	@Autowired
	public FeedTomlService(FeedUrlRepository feedUrlRepository) {
		this(feedUrlRepository, Clock.systemDefaultZone());
	}

	FeedTomlService(FeedUrlRepository feedUrlRepository, Clock clock) {
		this.feedUrlRepository = feedUrlRepository;
		this.clock = clock;
	}

	public String exportToml(boolean activeOnly) {
		StringBuilder toml = new StringBuilder();
		toml.append("schema_version = 1\n");
		toml.append("generated_at = \"").append(OffsetDateTime.now(clock)).append("\"\n");
		for (FeedUrl feed : feedUrlRepository.findAllForExport(activeOnly)) {
			toml.append("\n[[feeds]]\n");
			toml.append("url = \"").append(escape(feed.url())).append("\"\n");
			toml.append("status = \"").append(feed.status()).append("\"\n");
			if (FeedStatus.UNSUBSCRIBED.value().equals(feed.status())) {
				appendString(toml, "unsubscribe_reason", feed.unsubscribeReason());
				if (feed.unsubscribedAt() != null) {
					toml.append("unsubscribed_at = \"").append(feed.unsubscribedAt()).append("\"\n");
				}
				appendString(toml, "note", feed.note());
			}
		}
		return toml.toString();
	}

	@Transactional
	public ImportResult importToml(String toml, ImportOptions options) {
		List<ImportFeed> feeds = parse(toml);
		ImportCounters counters = new ImportCounters();
		List<String> conflicts = new ArrayList<>();

		for (ImportFeed feed : feeds) {
			var existing = feedUrlRepository.findByUrl(feed.url());
			if (existing.isEmpty()) {
				counters.inserted++;
				if (FeedStatus.UNSUBSCRIBED.value().equals(feed.status())) {
					counters.unsubscribed++;
				}
				if (!options.dryRun()) {
					feedUrlRepository.insertFromImport(feed.toFeedUrl());
				}
				continue;
			}

			FeedUrl current = existing.get();
			if (FeedStatus.ACTIVE.value().equals(current.status())
					&& FeedStatus.ACTIVE.value().equals(feed.status())) {
				counters.unchanged++;
			} else if (FeedStatus.ACTIVE.value().equals(current.status())
					&& FeedStatus.UNSUBSCRIBED.value().equals(feed.status())) {
				counters.updated++;
				counters.unsubscribed++;
				if (!options.dryRun()) {
					feedUrlRepository.unsubscribe(feed.url(), feed.unsubscribeReason(), feed.unsubscribedAt(), feed.note());
				}
			} else if (FeedStatus.UNSUBSCRIBED.value().equals(current.status())
					&& FeedStatus.UNSUBSCRIBED.value().equals(feed.status())) {
				counters.updated++;
				if (!options.dryRun()) {
					feedUrlRepository.updateUnsubscribedMetadata(
							feed.url(), feed.unsubscribeReason(), feed.unsubscribedAt(), feed.note());
				}
			} else if (FeedStatus.UNSUBSCRIBED.value().equals(current.status())
					&& FeedStatus.ACTIVE.value().equals(feed.status())) {
				if (options.resubscribe()) {
					counters.updated++;
					counters.resubscribed++;
					if (!options.dryRun()) {
						feedUrlRepository.resubscribe(feed.url());
					}
				} else {
					counters.conflicts++;
					conflicts.add("feed[" + feed.index() + "] " + feed.url()
							+ " is unsubscribed in DB but active in TOML");
				}
			}
		}

		return new ImportResult(
				counters.inserted,
				counters.updated,
				counters.unchanged,
				counters.unsubscribed,
				counters.resubscribed,
				counters.conflicts,
				List.of(),
				conflicts);
	}

	public List<ImportFeed> parse(String toml) {
		List<String> errors = new ArrayList<>();
		Map<String, String> root = new HashMap<>();
		List<Map<String, String>> feedTables = new ArrayList<>();
		Map<String, String> currentFeed = null;

		String[] lines = toml.split("\\R", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = stripComment(lines[i]).trim();
			if (line.isEmpty()) {
				continue;
			}
			if ("[[feeds]]".equals(line)) {
				currentFeed = new HashMap<>();
				feedTables.add(currentFeed);
				continue;
			}
			int equals = line.indexOf('=');
			if (equals < 0) {
				errors.add("line " + (i + 1) + ": expected key = value");
				continue;
			}
			String key = line.substring(0, equals).trim();
			String value = line.substring(equals + 1).trim();
			(currentFeed == null ? root : currentFeed).put(key, value);
		}

		if (!root.containsKey("schema_version")) {
			errors.add("schema_version is required");
		} else if (!"1".equals(root.get("schema_version"))) {
			errors.add("unsupported schema_version: " + root.get("schema_version"));
		}

		Set<String> seenUrls = new HashSet<>();
		List<ImportFeed> feeds = new ArrayList<>();
		for (int i = 0; i < feedTables.size(); i++) {
			Map<String, String> table = feedTables.get(i);
			int index = i + 1;
			String rawUrl = stringValue(table.get("url"), "feeds[" + index + "].url", errors);
			String url = null;
			if (rawUrl == null) {
				errors.add("feeds[" + index + "].url is required");
			} else {
				try {
					url = FeedUrlNormalizer.normalizeStrict(rawUrl);
					if (!seenUrls.add(url)) {
						errors.add("feeds[" + index + "].url duplicates another feed after normalization: " + url);
					}
				} catch (IllegalArgumentException e) {
					errors.add("feeds[" + index + "].url: " + e.getMessage());
				}
			}

			String status = stringValue(table.get("status"), "feeds[" + index + "].status", errors);
			if (status == null) {
				status = FeedStatus.ACTIVE.value();
			}
			if (!FeedStatus.ACTIVE.value().equals(status) && !FeedStatus.UNSUBSCRIBED.value().equals(status)) {
				errors.add("feeds[" + index + "].status must be active or unsubscribed: " + status);
			}

			String reason = stringValue(table.get("unsubscribe_reason"),
					"feeds[" + index + "].unsubscribe_reason", errors);
			if (FeedStatus.UNSUBSCRIBED.value().equals(status) && reason == null) {
				reason = UnsubscribeReason.OTHER.value();
			}
			if (reason != null) {
				try {
					UnsubscribeReason.fromValue(reason);
				} catch (IllegalArgumentException e) {
					errors.add("feeds[" + index + "].unsubscribe_reason is invalid: " + reason);
				}
			}

			OffsetDateTime unsubscribedAt = null;
			String rawUnsubscribedAt = stringValue(table.get("unsubscribed_at"),
					"feeds[" + index + "].unsubscribed_at", errors);
			if (rawUnsubscribedAt != null) {
				try {
					unsubscribedAt = OffsetDateTime.parse(rawUnsubscribedAt);
				} catch (RuntimeException e) {
					errors.add("feeds[" + index + "].unsubscribed_at must be an offset date-time: " + rawUnsubscribedAt);
				}
			}
			String note = stringValue(table.get("note"), "feeds[" + index + "].note", errors);
			if (FeedStatus.ACTIVE.value().equals(status)) {
				reason = null;
				unsubscribedAt = null;
				note = null;
			}

			if (url != null) {
				feeds.add(new ImportFeed(index, url, status, reason, unsubscribedAt, note));
			}
		}

		if (!errors.isEmpty()) {
			throw new TomlValidationException(errors);
		}
		return feeds;
	}

	private void appendString(StringBuilder toml, String key, String value) {
		if (value != null) {
			toml.append(key).append(" = \"").append(escape(value)).append("\"\n");
		}
	}

	private String stringValue(String value, String field, List<String> errors) {
		if (value == null) {
			return null;
		}
		if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
			errors.add(field + " must be a TOML string");
			return null;
		}
		return unescape(value.substring(1, value.length() - 1), field, errors);
	}

	private String stripComment(String line) {
		boolean inString = false;
		boolean escaping = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (escaping) {
				escaping = false;
			} else if (c == '\\' && inString) {
				escaping = true;
			} else if (c == '"') {
				inString = !inString;
			} else if (c == '#' && !inString) {
				return line.substring(0, i);
			}
		}
		return line;
	}

	private String escape(String value) {
		return value.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	private String unescape(String value, String field, List<String> errors) {
		StringBuilder result = new StringBuilder();
		boolean escaping = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (!escaping) {
				if (c == '\\') {
					escaping = true;
				} else {
					result.append(c);
				}
				continue;
			}
			switch (c) {
				case '\\' -> result.append('\\');
				case '"' -> result.append('"');
				case 'n' -> result.append('\n');
				case 'r' -> result.append('\r');
				case 't' -> result.append('\t');
				default -> {
					errors.add(field + " contains unsupported escape: \\" + c);
					result.append(c);
				}
			}
			escaping = false;
		}
		if (escaping) {
			errors.add(field + " ends with an incomplete escape");
		}
		return result.toString();
	}

	private static class ImportCounters {
		int inserted;
		int updated;
		int unchanged;
		int unsubscribed;
		int resubscribed;
		int conflicts;
	}

	public record ImportOptions(boolean dryRun, boolean resubscribe) {
	}

	public record ImportResult(
			int inserted,
			int updated,
			int unchanged,
			int unsubscribed,
			int resubscribed,
			int conflicts,
			List<String> errors,
			List<String> conflictMessages) {
	}

	public record ImportFeed(
			int index,
			String url,
			String status,
			String unsubscribeReason,
			OffsetDateTime unsubscribedAt,
			String note) {

		FeedUrl toFeedUrl() {
			return new FeedUrl(HashIds.md5(url), url, status, unsubscribeReason, unsubscribedAt, note);
		}
	}

	public static class TomlValidationException extends RuntimeException {
		private final List<String> errors;

		TomlValidationException(List<String> errors) {
			super(String.join("; ", errors));
			this.errors = List.copyOf(errors);
		}

		public List<String> errors() {
			return errors;
		}
	}
}
