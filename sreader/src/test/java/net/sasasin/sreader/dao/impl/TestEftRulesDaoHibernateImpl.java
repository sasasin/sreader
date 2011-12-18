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
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

import java.util.List;

import net.sasasin.sreader.orm.EftRules;
import net.sasasin.sreader.testcasesupport.AbstractHibernateTestCase;
import net.sasasin.sreader.util.Md5Util;

import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestEftRulesDaoHibernateImpl extends AbstractHibernateTestCase {

	private EftRulesDaoHibernateImpl testee = null;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		testee = new EftRulesDaoHibernateImpl();
		testee.setSessionFactory(getSessionFactory());

		// テストデータ投入
		IDataSet dataSet = new XlsDataSet(getClass().getResourceAsStream(
				getClass().getSimpleName() + ".xls"));
		DatabaseOperation.CLEAN_INSERT.execute(getConnection(), dataSet);
	}

	@After
	@Override
	public void tearDown() throws Exception {
		super.tearDown();
	}

	@Override
	protected IDataSet getDataSet() throws Exception {
		return new DefaultDataSet();
	}

	/**
	 * データが存在するケース。
	 */
	@Test
	public void testFindAll() {

		List<EftRules> actual = testee.findAll();

		assertThat(actual.size(), not(0));
	}

	//TODO 事前にデータを消す方法を考える
	// /**
	// * データが存在しないケース。
	// *
	// * @throws Exception
	// */
	// @Test
	// @Ignore
	// public void testFindAllNotFound() {
	//
	// // データを消す
	// try {
	// IDataSet dataSet = new XlsDataSet(this.getClass()
	// .getResourceAsStream(
	// this.getClass().getSimpleName() + ".xls"));
	// DatabaseOperation.DELETE_ALL.execute(getConnection(), dataSet);
	// } catch (Exception e) {
	// // データを消すのに失敗？
	// e.printStackTrace();
	// }
	//
	// List<EftRules> actual = testee.findAll();
	//
	// assertThat(actual.size(), is(0));
	// }

	/**
	 * 検索対象のデータが存在するケース。
	 */
	@Test
	public void testGet() {
		EftRules actual = testee.get("381edb1a6f29683e5df435cb218b862b");

		// 取れているかどうかの検証
		assertThat(actual, notNullValue());
		// データの中身の検証
		assertThat(actual.getId(), is("381edb1a6f29683e5df435cb218b862b"));
		assertThat(actual.getUrl(), is("^http://example.com/archive/"));
		assertThat(actual.getExtractRule(), is("//div[@contents or @comments]"));

	}

	/**
	 * 検索対象のデータが存在しないケース。
	 */
	@Test
	public void testGetNotFount() {

		EftRules actual = testee.get("HOGE");

		assertThat(actual, nullValue());
	}

	/**
	 * キー重複がなく、Insertに成功するケース。
	 */
	@Test
	public void testSave() {

		EftRules expected = new EftRules();
		expected.setId(Md5Util.crypt("^http://example.net/"));
		expected.setUrl("^http://example.net/");
		expected.setExtractRule("testcase:testSave()");

		// プライマリキーが返る
		String actual = testee.save(expected);

		assertThat(actual, is(expected.getId()));
		// 本当に入ったか、取得してみる
		EftRules actualEntity = testee.get(actual);
		// ポインタは別物
		assertThat(actualEntity, not(expected));
		// 各値は同じ
		assertThat(actualEntity.getId(), is(expected.getId()));
		assertThat(actualEntity.getUrl(), is(expected.getUrl()));
		assertThat(actualEntity.getExtractRule(), is(expected.getExtractRule()));

	}

	/**
	 * キー重複によりInsertに失敗するケース。
	 */
	@Test
	public void testSaveFail() {
		EftRules expected = new EftRules();
		expected.setId("381edb1a6f29683e5df435cb218b862b");
		expected.setUrl("");
		expected.setExtractRule("");

		try {
			String actual = testee.save(expected);
			fail("Insert成功はテスト失敗");
		} catch (ConstraintViolationException e) {
			// 本当に入っていないか、取得してみる
			EftRules actualEntity = testee.get(expected.getId());
			// ポインタは別物
			assertThat(actualEntity, not(expected));
			// プライマリキーの同じものがいるならOK
			assertThat(actualEntity.getId(), is(expected.getId()));
		}
	}

	/**
	 * 削除対象のデータが存在し、成功するケース
	 */
	@Test
	public void testDelete() {

		EftRules expected = testee.get("381edb1a6f29683e5df435cb218b862b");

		testee.delete(expected);

		EftRules actual = testee.get("381edb1a6f29683e5df435cb218b862b");

		assertThat(actual, nullValue());
	}

	/**
	 * 削除対象のデータが存在せず、失敗するケース。
	 */
	@Test
	public void testDeleteFail() {

		EftRules expected = new EftRules();
		expected.setId("HOGE");
		expected.setUrl("");
		expected.setExtractRule("");
		try {
			testee.delete(expected);
		} catch (HibernateException e) {
			fail("Delete失敗しても例外は出ないはず");
		}
	}

	/**
	 * 更新対象のデータが存在し、成功するケース。
	 */
	@Test
	public void testUpdate() {
		// 更新対象を取得
		EftRules expected = testee.get("381edb1a6f29683e5df435cb218b862b");
		expected.setExtractRule("testcase testUpdate()");

		testee.update(expected);
		// 更新されているか、再取得してみる
		EftRules actual = testee.get("381edb1a6f29683e5df435cb218b862b");
		// ポインタは別物
		assertThat(actual, not(expected));
		// 各値は同じ
		assertThat(actual.getId(), is(expected.getId()));
		assertThat(actual.getUrl(), is(expected.getUrl()));
		assertThat(actual.getExtractRule(), is(expected.getExtractRule()));

	}

	/**
	 * 更新対象のデータが存在せず、失敗するケース。
	 */
	@Test
	public void testUpdateFail() {
		// 更新対象を取得
		EftRules expected = testee.get("381edb1a6f29683e5df435cb218b862b");
		expected.setExtractRule("testcase testUpdate()");
		// 更新対象を消しておく
		testee.delete(expected);
		try {
			testee.update(expected);
			fail("update失敗してるのに例外が出ないのは失敗");
		} catch (HibernateException e) {
			// テスト対象存在せず、的な？
		}
	}

}
