/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2011, Shinnosuke Suzuki <sasasin@sasasin.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *	
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
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
