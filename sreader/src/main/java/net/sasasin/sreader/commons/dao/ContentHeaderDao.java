package net.sasasin.sreader.commons.dao;

import java.util.List;

import net.sasasin.sreader.commons.entity.ContentHeader;

public interface ContentHeaderDao extends GenericDao<ContentHeader, String> {
	
	/**
	 * ContentFullText未取得のContentHeaderのリストを返す。
	 * 
	 * @return
	 */
	public List<ContentHeader> findByConditionOfFullTextNotFetched();

}
