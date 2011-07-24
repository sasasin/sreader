package net.sasasin.sreader;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arnx.jsonic.JSON;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.Wget;

public class EftRulesDriver {
	private URL url = null;

	public static void main(String[] args){
		new EftRulesDriver().run();
	}
	
	public EftRulesDriver() {
	}

	public void run() {
		try {
			url = new URL("http://wedata.net/databases/LDRFullFeed/items.json");
			Map<String, String> json = getJson();
			importEftRules(json);
		} catch (MalformedURLException e) {
		}

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Map<String, String> getJson() {
		Map<String, String> ldrFullFeed = new HashMap<String, String>();

		String s = new Wget(url).read();

		for (Map m : (List<Map>) JSON.decode(s)) {
			Map<String, String> data = (Map<String, String>) m.get("data");
			ldrFullFeed.put(data.get("url"), data.get("xpath"));
		}

		return ldrFullFeed;
	}

	public void importEftRules(Map<String, String> json) {
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();

			// EFT_RULES初期化用SQL
			conn.createStatement().executeUpdate("delete from eft_rules");

			// 重複チェック用SQL
			PreparedStatement sel = conn
					.prepareStatement("select count(*) from eft_rules where url = ?");

			// 投入用SQL
			PreparedStatement up = conn
					.prepareStatement("insert into eft_rules(url, extract_rule) values(?, ?)");

			for (String key : json.keySet()) {
				// 投入前に重複チェック
				sel.setString(1, key);
				ResultSet rs = sel.executeQuery();
				rs.next();
				if (rs.getInt(1) < 1) {
					// キーで探して居なければ投入
					up.setString(1, key);
					up.setString(2, json.get(key));
					up.executeUpdate();
				}
				rs.close();
			}

			conn.commit();

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
			}
		}
	}
}
