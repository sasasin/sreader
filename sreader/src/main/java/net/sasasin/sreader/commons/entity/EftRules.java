package net.sasasin.sreader.commons.entity;

// Generated Sep 11, 2011 1:46:23 AM by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * EftRules generated by hbm2java
 */
@Entity
@Table(name = "eft_rules")
public class EftRules implements java.io.Serializable {

	private static final long serialVersionUID = -6267985787650434669L;
	private String id;
	private String url;
	private String extractRule;

	public EftRules() {
	}

	public EftRules(String id, String url, String extractRule) {
		this.id = id;
		this.url = url;
		this.extractRule = extractRule;
	}

	@Id
	@Column(name = "id", unique = true, nullable = false, length = 32)
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Column(name = "url", nullable = false, length = 8096)
	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Column(name = "extract_rule", nullable = false, length = 8096)
	public String getExtractRule() {
		return this.extractRule;
	}

	public void setExtractRule(String extractRule) {
		this.extractRule = extractRule;
	}

}