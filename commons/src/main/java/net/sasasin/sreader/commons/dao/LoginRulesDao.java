package net.sasasin.sreader.commons.dao;

import net.sasasin.sreader.commons.entity.LoginRules;

public interface LoginRulesDao extends GenericDao<LoginRules, String> {
	
	/**
	 * ホストネームをキーに、LoginRulesを取得する。存在しない場合はnullを返す。
	 * 
	 * @param hostName
	 * @return
	 */
	public LoginRules getByHostname(String hostName);
}
