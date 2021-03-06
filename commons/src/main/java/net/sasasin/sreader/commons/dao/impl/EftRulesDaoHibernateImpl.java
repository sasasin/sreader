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
package net.sasasin.sreader.commons.dao.impl;

import java.net.URL;

import net.sasasin.sreader.commons.dao.EftRulesDao;
import net.sasasin.sreader.commons.entity.EftRules;

import org.hibernate.Session;

/**
 * @author sasasin
 * 
 */
public class EftRulesDaoHibernateImpl extends
		GenericDaoHibernateImpl<EftRules, String> implements EftRulesDao {

	@Override
	public EftRules getByUrl(URL url) {
		
		String sql = "select e.* from eft_rules e where :url regexp replace(substring_index(url, '(?!', '1'),'(?:','(') "
				+ "order by length(replace(substring_index(url, '(?!', '1'),'(?:','(')) desc";

		Session ses = getSessionFactory().openSession();

		EftRules er = (EftRules) ses.createSQLQuery(sql)
				.addEntity(EftRules.class).setParameter("url", url.toString())
				.setMaxResults(1).uniqueResult();
		
		return er;
	}
}
