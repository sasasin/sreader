-- GMailログイン情報のサンプルデータ。
delete from gmail_login_info;

insert into gmail_login_info(address, password, account_id)
values('fugafuga@gmail.com', 'piyopiyo', 'hoge');

commit;
