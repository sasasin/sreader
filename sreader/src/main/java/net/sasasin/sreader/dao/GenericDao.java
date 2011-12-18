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
package net.sasasin.sreader.dao;

import java.io.Serializable;
import java.util.List;

import org.hibernate.SessionFactory;

public interface GenericDao<T, PK extends Serializable> {

	/**
	 * プライマリキーを検索に、一件取得する。存在しない場合はnullを返す。
	 * @param id
	 * @return
	 */
	public T get(PK id);

	/**
	 * テーブルから全件取得する。テーブルに一件も存在しない場合は、List#size()==0のリストを返す。
	 * @return
	 */
	public List<T> findAll();

	/**
	 * テーブルに一件、insertする。
	 * @param entity
	 * @return プライマリキー
	 */
	public PK save(T entity);

	/**
	 * entityの内容で、レコードを更新する。更新対象が存在しない場合、例外などは発生せず、正常終了する。
	 * @param entity
	 */
	public void update(T entity);

	/**
	 * entityに一致するレコードを、テーブルから削除する。削除対象が存在しない場合、例外などは発生せず、正常終了する。
	 * @param entity
	 */
	public void delete(T entity);
	
	public void setSessionFactory(SessionFactory sessionFactory);
}
