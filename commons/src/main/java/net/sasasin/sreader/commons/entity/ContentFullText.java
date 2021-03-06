package net.sasasin.sreader.commons.entity;

// Generated Sep 11, 2011 1:46:23 AM by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * ContentFullText generated by hbm2java
 */
@Entity
@Table(name = "content_full_text")
public class ContentFullText implements java.io.Serializable {

	private static final long serialVersionUID = 4304190925566911717L;
	private String id;
	private ContentHeader contentHeader;
	private String fullText;

	public ContentFullText() {
	}

	public ContentFullText(String id, ContentHeader contentHeader) {
		this.id = id;
		this.contentHeader = contentHeader;
	}

	public ContentFullText(String id, ContentHeader contentHeader,
			String fullText) {
		this.id = id;
		this.contentHeader = contentHeader;
		this.fullText = fullText;
	}

	@Id
	@Column(name = "id", unique = true, nullable = false, length = 32)
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "content_header_id", nullable = false)
	public ContentHeader getContentHeader() {
		return this.contentHeader;
	}

	public void setContentHeader(ContentHeader contentHeader) {
		this.contentHeader = contentHeader;
	}

	@Column(name = "full_text")
	public String getFullText() {
		return this.fullText;
	}

	public void setFullText(String fullText) {
		this.fullText = fullText;
	}

}
