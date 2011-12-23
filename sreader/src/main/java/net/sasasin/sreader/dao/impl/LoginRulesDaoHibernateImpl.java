package net.sasasin.sreader.dao.impl;

import net.sasasin.sreader.dao.LoginRulesDao;
import net.sasasin.sreader.orm.LoginRules;

import org.hibernate.Session;

public class LoginRulesDaoHibernateImpl extends
		GenericDaoHibernateImpl<LoginRules, String> implements LoginRulesDao {

	@Override
	public LoginRules getByHostname(String hostName) {

		LoginRules lr = null;
		Session ses = getSessionFactory().openSession();

		lr = (LoginRules) ses
				.createQuery(
						"from LoginRules lr where lr.hostName = :p_hostName")
				.setParameter("p_hostName", hostName).setMaxResults(1)
				.uniqueResult();

		return lr;
	}

}
