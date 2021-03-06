package net.sasasin.sreader.commons.entity;

// Generated Sep 11, 2011 1:46:23 AM by Hibernate Tools 3.4.0.CR1

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * Account generated by hbm2java
 */
@Entity
@Table(name = "account")
public class Account implements java.io.Serializable {

	private static final long serialVersionUID = 4382699510893342066L;
	private String id;
	private String email;
	private String password;
	private Set<Subscriber> subscribers = new HashSet<Subscriber>(0);

	public Account() {
	}

	public Account(String id, String email, String password) {
		this.id = id;
		this.email = email;
		this.password = password;
	}

	public Account(String id, String email, String password,
			Set<Subscriber> subscribers) {
		this.id = id;
		this.email = email;
		this.password = password;
		this.subscribers = subscribers;
	}

	@Id
	@Column(name = "id", unique = true, nullable = false, length = 32)
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Column(name = "email", nullable = false, length = 1024)
	public String getEmail() {
		return this.email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	@Column(name = "password", nullable = false, length = 1024)
	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "account")
	public Set<Subscriber> getSubscribers() {
		return this.subscribers;
	}

	public void setSubscribers(Set<Subscriber> subscribers) {
		this.subscribers = subscribers;
	}

}
