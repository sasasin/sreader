-- GMailログイン情報のサンプルデータ。
delete from account;

insert into account(id, email, password)
values('1', 'fugafuga@gmail.com', 'piyopiyo');

commit;
