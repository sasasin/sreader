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

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import net.sasasin.sreader.dao.GenericDao;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;

/**
 * {@link GenericDao}のHibernateによる実装。
 * 
 * @author sasasin
 * 
 */
public class GenericDaoHibernateImpl<T, PK extends Serializable> implements
		GenericDao<T, PK> {

	@SuppressWarnings("unchecked")
	private final Class<T> type = (Class<T>) ((ParameterizedType) getClass()
			.getGenericSuperclass()).getActualTypeArguments()[0];

	private SessionFactory sessionFactory;

	protected SessionFactory getSessionFactory() {
		if (sessionFactory == null) {
			sessionFactory = new Configuration().configure()
					.buildSessionFactory();
		}
		return sessionFactory;
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	protected Class<T> getType() {

		return this.type;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T get(PK id) {

		T entity = (T) getSessionFactory().openSession().get(getType(), id);

		return entity;

	}

	@SuppressWarnings("unchecked")
	@Override
	public List<T> findAll() {
		Session s = getSessionFactory().openSession();

		List<T> result = s.createCriteria(getType())
				.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list();

		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public PK save(T entity) {
		Session s = getSessionFactory().openSession();
		Transaction tx = s.beginTransaction();
		try {
			PK id = (PK) s.save(entity);
			tx.commit();
			return id;
		} catch (HibernateException e) {
			// キー重複によるinsert失敗。ロールバックし、セッションは閉じる。
			e.printStackTrace();
			tx.rollback();
			s.close();
			throw e;
		}
	}

	@Override
	public void update(T entity) {
		Session s = getSessionFactory().openSession();
		Transaction tx = s.beginTransaction();
		try {
			s.update(entity);
			tx.commit();
		} catch (HibernateException e) {
			// update失敗。ロールバックし、セッションは閉じる。
			e.printStackTrace();
			tx.rollback();
			s.close();
			throw e;
		}
	}

	@Override
	public void delete(T entity) {
		Session s = getSessionFactory().openSession();
		Transaction tx = s.beginTransaction();
		try {
			s.delete(entity);
			tx.commit();
		} catch (HibernateException e) {
			// delete失敗。ロールバックし、セッションは閉じる。
			e.printStackTrace();
			tx.rollback();
			s.close();
			throw e;
		}
	}

}
