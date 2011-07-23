-- GMailログイン情報のサンプルデータ。
-- delete from gmail_login_info;
-- insert into gmail_login_info(address, password, account_id)
-- values('fugafuga@gmail.com', 'piyopiyo', 'hoge');

-- ログインルールのサンプル。
delete from login_rules;

insert into login_rules(host_name, post_url, id_box_name, password_box_name) 
values('jp.wsj.com', 'http://jp.wsj.com/user/login', 'Login', 'Password');

insert into login_rules(host_name, post_url, id_box_name, password_box_name)
values('jbpress.ismedia.jp', 'https://jbpress.ismedia.jp/auth/dologin', 'login', 'password');

commit;

-- 全文抽出ルールの中身。
-- エスケープシーケンスに注意。SQLとJavaで通るよう調整する。
delete from eft_rules;

insert into eft_rules(url, extract_rule)
values('http://jp.wsj.com/', '<div class=\"articlePage\">(.*?)</div><!--article_story_body-->');

insert into eft_rules(url, extract_rule)
values('http://jp.wsj.com/japanrealtime/','<div class="articlePage">(.*?)<h3 class=\"byline\">');

insert into eft_rules(url, extract_rule)
values('http://www3.nhk.or.jp/news', '<p id=\"news_textbody\">(.*?)<!-- _ria_tail_ -->');

insert into eft_rules(url, extract_rule)
values('http://blog.livedoor.jp/minnanohimatubushi/archives', '<div class=\"article-body entry-content\">(.*?)<!-- articleBody End -->');

commit;
