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
