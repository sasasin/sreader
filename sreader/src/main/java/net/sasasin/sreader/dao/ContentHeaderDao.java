package net.sasasin.sreader.dao;

import java.util.List;

import net.sasasin.sreader.orm.ContentHeader;

public interface ContentHeaderDao extends GenericDao<ContentHeader, String> {
	
	/**
	 * ContentFullText未取得のContentHeaderのリストを返す。
	 * 
	 * @return
	 */
	public List<ContentHeader> findByConditionOfFullTextNotFetched();

}
