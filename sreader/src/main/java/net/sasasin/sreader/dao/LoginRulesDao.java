package net.sasasin.sreader.dao;

import net.sasasin.sreader.orm.LoginRules;

public interface LoginRulesDao extends GenericDao<LoginRules, String> {
	
	/**
	 * ホストネームをキーに、LoginRulesを取得する。存在しない場合はnullを返す。
	 * 
	 * @param hostName
	 * @return
	 */
	public LoginRules getByHostname(String hostName);
}
