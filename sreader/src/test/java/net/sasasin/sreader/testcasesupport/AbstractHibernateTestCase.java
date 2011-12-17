/**
 * 
 */
package net.sasasin.sreader.testcasesupport;

import org.dbunit.DBTestCase;
import org.dbunit.PropertiesBasedJdbcDatabaseTester;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sasasin
 * 
 */
public abstract class AbstractHibernateTestCase extends DBTestCase {

	private static final Logger logger = LoggerFactory
			.getLogger(AbstractHibernateTestCase.class);

	private static SessionFactory sessionFactory;
	private Session session;

	@Before
	@Override
	public void setUp() throws Exception {

		Configuration cfg = new Configuration().configure();
		if (sessionFactory == null) {
			sessionFactory = cfg.buildSessionFactory();
		}
		session = sessionFactory.openSession();

		// Hibernateと接続情報を共有する。接続自体を共有するわけではない
		System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME,
				cfg.getProperty("hibernate.connection.username"));
		System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD,
				cfg.getProperty("hibernate.connection.password"));
		System.setProperty(
				PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL,
				cfg.getProperty("hibernate.connection.url"));
		System.setProperty(
				PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS,
				cfg.getProperty("hibernate.connection.driver_class"));

		logger.debug("DBUNIT_USERNAME "
				+ System.getProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME));
		logger.debug("DBUNIT_PASSWORD "
				+ System.getProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD));
		logger.debug("DBUNIT_CONNECTION_URL "
				+ System.getProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL));
		logger.debug("DBUNIT_DRIVER_CLASS "
				+ System.getProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS));

		super.setUp();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		session.close();
		super.tearDown();
	}
	
	/**
	 * テスト用スキーマに接続するためのSessionFactoryを返す。
	 * 各テストケースはDAOのSessionFactoryにこれをセットしてテストする。
	 * 
	 * @return SessionFactory
	 */
	protected SessionFactory getSessionFactory(){
		return sessionFactory;
	}
	
	@Override
	protected IDataSet getDataSet() throws Exception {
		return new DefaultDataSet();
	}
}
