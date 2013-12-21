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

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import net.sasasin.sreader.dao.AccountDao;
import net.sasasin.sreader.orm.Account;
import net.sasasin.sreader.testcasesupport.AbstractHibernateTestCase;

import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Before;
import org.junit.Test;

/**
 * @author sasasin
 * 
 */
public class TestAccountDaoHibernateImpl extends AbstractHibernateTestCase {

	private AccountDao testee = null;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		testee = new AccountDaoHibernateImpl();

		// テストデータ投入
		IDataSet dataSet = new XlsDataSet(getClass().getResourceAsStream(
				getClass().getSimpleName() + ".xls"));
		DatabaseOperation.CLEAN_INSERT.execute(getConnection(), dataSet);
	}

	/**
	 * {@link AccountDao#getOneResult()}の、データが存在する場合のテスト。
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetOneResult() throws Exception {

		Account actual = testee.getOneResult();
		// 取れていればnullではない
		assertThat(actual, notNullValue());
		// 中身も見ておく
		assertThat(actual.getId(), is("4d063af85d88d58976e386b1249df55e"));
		assertThat(actual.getEmail(), is("fuga@example.com"));
		assertThat(actual.getPassword(), is("fugafuga"));
	}

	/**
	 * {@link AccountDao#getOneResult()}の、データが存在しない場合のテスト。
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetOneResultNotFound() throws Exception {

		// accountを空にする
		DatabaseOperation.DELETE_ALL.execute(getConnection(),
				new DefaultDataSet(new DefaultTable("account")));

		Account actual = testee.getOneResult();

		assertThat(actual, nullValue());

	}
}
