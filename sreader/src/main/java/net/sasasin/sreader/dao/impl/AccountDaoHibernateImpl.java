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

import org.hibernate.criterion.Order;

import net.sasasin.sreader.dao.AccountDao;
import net.sasasin.sreader.orm.Account;

/**
 * @author sasasin
 * 
 */
public class AccountDaoHibernateImpl extends
		GenericDaoHibernateImpl<Account, String> implements AccountDao {

	@Override
	public Account getOneResult() {

		Account entity = (Account) getSessionFactory().openSession()
				.createCriteria(getType()).addOrder(Order.asc("id"))
				.setMaxResults(1).uniqueResult();

		return entity;
	}
}
