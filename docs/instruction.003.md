あなたは Java / Maven / MySQL / Flyway / Docker に詳しいソフトウェア移行エンジニアです。

対象リポジトリは `sasasin/sreader` です。このソフトウェアは約13年間更新停止していましたが、現在は現代化作業を進行中です。

すでに以下は master にマージ済みです。

* Docker Compose ベースの開発環境
* Maven multi-module 化
* 依存バージョン固定
* MySQL 8.4 対応
* Flyway 導入
* DB/user 作成と schema/data migration の分離

今回の作業では、運用安全性を優先して、以下の2つの機能を丸ごと廃止してください。

1. Gmail アカウントを用いてメール送信する機能
2. 購読先ログイン ID/パスワード、ログインルールを用いてログインしてコンテンツ取得する機能

「認証情報を暗号化する」「Gmail OAuth2 に移行する」「secret 管理を強化する」のではありません。今回は、これらの機能自体を削除します。

## 最重要方針

* Gmail 送信機能は残さないでください。
* SMTP / Gmail / JavaMail / メール配信関連のコード、設定、依存、テスト、ドキュメントを削除または無効化してください。
* 購読先へのログイン機能は残さないでください。
* `auth_name`, `auth_password`, `login_rules` など、外部サイトログイン用の DB 項目・テーブル・seed・コードを削除してください。
* 実 Gmail アカウント、実パスワード、購読先ログイン情報を扱う前提をドキュメントからも消してください。
* 残す機能は、原則として「認証不要な公開 RSS/Atom feed を取得し、記事本文を抽出・保存する」範囲に限定してください。
* 互換性維持のために removed feature の no-op 実装を残すのは避けてください。不要な dead code は削除してください。
* ただし、一度に関係ない大規模リファクタリングはしないでください。

## ホスト環境に関する制約

* ホスト OS に `mvn`, `java`, `javac`, `mysql`, `flyway` をインストールしないでください。
* ホスト側で直接実行してよいのは、原則として `git`, `docker`, `docker compose`, 基本的なファイル確認コマンドだけです。
* Maven、Java、MySQL client、Flyway は必ず Docker Compose 経由で実行してください。
* 既存の Docker Compose / Flyway 構成を壊さないでください。

## まず現況を調査してください

Codex は、このリポジトリの全容をまだ把握していない前提で進めてください。

最初に以下を調査してください。

* `README.md`
* `docs/modernization.md`
* `docker-compose.yml`
* `.env.example`
* root `pom.xml`
* `commons/pom.xml`
* `batch/pom.xml`
* `db/migration/`
* `docker/mysql/init/`
* `commons/script/`
* `commons/src/main/java`
* `batch/src/main/java`
* `commons/src/test`
* `batch/src/test`

特に以下の語で全体検索してください。

```sh
git grep -n -E "Gmail|gmail|mail|Mail|SMTP|smtp|JavaMail|javax.mail|jakarta.mail|account|password|auth_name|auth_password|login_rules|LoginRule|subscriber|Subscriber|publish_log|content_view|WebClient|HtmlUnit|login"
```

検索結果を見て、以下を分類してください。

1. Gmail / SMTP / メール送信に関係するもの
2. 外部サイトログインに関係するもの
3. RSS/Atom 取得や全文抽出に必要なもの
4. DB schema / migration / test fixture に関係するもの
5. ドキュメントだけのもの

分類後、短い作業方針を出してから変更に着手してください。

## 廃止対象 1: Gmail アカウントを用いたメール送信機能

以下を削除または無効化してください。

* Gmail 送信処理
* SMTP 送信処理
* メール本文生成が Gmail 配信用にしか使われていない場合、その処理
* Gmail account / password を DB から読む処理
* `account.password` を使う処理
* `content_view` を使って未配信記事をメール送信する処理
* `publish_log` がメール送信済み管理だけに使われている場合、その table/entity/DAO/test
* Gmail 配信前提の shell script
* Gmail 配信前提の README / docs
* JavaMail / mail API / SMTP 関連 dependency

注意:

* `account` table が Gmail 配信先アカウントだけを表しているなら、table/entity/DAO/test ごと削除してください。
* `publish_log` が Gmail 配信済み管理だけを表しているなら削除してください。
* `content_view` が Gmail 配信用 view なら削除してください。
* 削除により batch の main flow が壊れる場合は、メール配信 phase を呼ばない構成に直してください。
* 代替のメール送信方式、OAuth2、SMTP relay などは実装しないでください。

## 廃止対象 2: 購読先ログイン ID/パスワード、ログインルールによるログイン取得機能

以下を削除または無効化してください。

* `subscriber.auth_name`
* `subscriber.auth_password`
* `subscriber.auth_check_date` がログイン機能専用なら削除
* `login_rules` table
* login rule entity / DAO / service
* login form 送信処理
* HtmlUnit / WebClient をログイン目的だけに使っているコード
* login rule seed data
* login rule test
* README / docs の「認証対応」説明
* 購読先 ID/password を DB に保存する説明

注意:

* HtmlUnit が認証不要ページの本文抽出にも使われている場合は、依存やコードを即削除せず、ログイン用途だけを削ってください。
* `subscriber` table が「アカウントごとの購読」と「外部サイト認証」の両方を担っている場合は、今後の設計を簡素化してください。
* `subscriber` が Gmail account と feed_url の関連だけに使われており、Gmail 配信廃止後に不要なら削除してください。
* `feed_url` が公開 RSS/Atom の管理に必要なら残してください。

## DB / Flyway migration 方針

既存の Flyway migration は master に入っているため、原則として既存の `V1__...` や `V2__...` を直接編集しないでください。既存 DB volume の checksum 不一致を避けるためです。

今回の schema 変更は、新しい migration として追加してください。

例:

```text
db/migration/
  V3__remove_email_delivery_and_authenticated_fetch.sql
```

この migration では、調査結果に基づき、不要になった table / view / column を削除してください。

候補:

```sql
DROP VIEW IF EXISTS content_view;

DROP TABLE IF EXISTS publish_log;
DROP TABLE IF EXISTS subscriber;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS login_rules;
```

ただし、実際に削除する対象はコードの使用状況を確認して決めてください。

もし `subscriber` を残す必要がある場合は、少なくとも以下のような認証関連 column は削除してください。

```sql
ALTER TABLE subscriber DROP COLUMN auth_name;
ALTER TABLE subscriber DROP COLUMN auth_password;
ALTER TABLE subscriber DROP COLUMN auth_check_date;
```

期待する最終 schema:

* Gmail account password を保存しない。
* 購読先 login ID/password を保存しない。
* `login_rules` を持たない。
* Gmail 配信用 `content_view` を持たない。
* メール送信済み管理専用の `publish_log` を持たない。
* 公開 RSS/Atom feed 取得と本文保存に必要な table だけを残す。

Fresh DB では `V1` / `V2` の後に `V3` が適用されるため、一時的に古い table が作られてから削除されても構いません。将来、migration を squash するかどうかは別作業として docs の残課題に書いてください。

## Java code 方針

以下の観点で削除・修正してください。

### 削除候補

* Gmail sender
* Mail publisher
* SMTP client
* Account password accessor
* Login rule entity
* Login rule DAO
* Login service
* Authenticated content fetcher
* Subscriber auth fields
* Gmail / login 関連 test
* Gmail / login 関連 fixture

### 残す候補

* 公開 RSS/Atom feed 取得
* feed URL 読み込み
* content header 保存
* content full text 保存
* 認証不要ページの本文抽出
* DAO のうち残存 table に対応するもの
* Flyway / Docker / Maven 基盤

判断に迷う場合は、削除対象が Gmail 送信または外部サイトログインに専用かどうかで判断してください。専用なら削除してください。共用なら、該当機能だけを切り離してください。

## Maven dependency 方針

POM を確認し、以下を整理してください。

* JavaMail / mail / activation 系 dependency が Gmail 送信専用なら削除
* HtmlUnit がログイン専用なら削除
* HtmlUnit が本文抽出にも必要なら残す
* Gmail / SMTP / login 専用 test dependency があれば削除
* 削除したコードにより不要になった dependency を削除
* unrelated な依存 update はしない

今回の目的は依存最新版追従ではありません。不要になった危険な機能と、それを支える依存を減らすことです。

## README / docs 更新

README と `docs/modernization.md` を更新してください。

必ず反映する内容:

* SReader は現代化中であること
* Gmail アカウントを用いたメール配信機能を廃止したこと
* 購読先ログイン ID/パスワードを使った認証付き取得機能を廃止したこと
* DB に Gmail password や購読先 password を保存しない方針に変えたこと
* `gmail.sql` は現代化後の標準手順では使わないこと
* `login_rules` は廃止されたこと
* 現在サポートする対象は、認証不要な公開 RSS/Atom feed の取得・本文抽出・DB 保存であること
* Docker Compose / Flyway による setup 手順
* 既存 Docker volume がある場合は、新 migration を適用するか、開発 DB を破棄して作り直す必要があること
* `docker compose down -v` はローカル DB を破棄するため注意が必要であること

README の旧説明にある以下の趣旨は削除または過去機能として明確に非推奨化してください。

* 認証対応
* Gmail で配信
* Gmail account 情報を DB に登録する
* `gmail.sql` を編集して使う
* `ddl.mysql.users.sql` を直接 mysql で実行する旧手順

## legacy SQL の扱い

`commons/script/` 配下の旧 SQL を確認してください。

特に以下を調べてください。

* `gmail.sql`
* `ddl.mysql.tables.sql`
* `dml.sql`
* `ddl.mysql.users.sql`

方針:

* 現代化後の標準手順では使わないものとして明記してください。
* `gmail.sql` は、現代化後の標準手順から外してください。
* `login_rules` や Gmail account seed が残っている場合は、legacy reference として残すか削除するかを判断してください。
* 残す場合は、ファイル先頭に legacy-only である旨をコメントしてください。
* 削除する場合は、影響を確認して docs に理由を書いてください。

## テスト方針

以下を実施してください。

* Gmail / SMTP / login rule / auth fetch に関する test は削除または書き換え
* 残す機能に対する test は通るようにする
* DB schema の変更に合わせて fixture を更新
* Flyway migration 適用後の schema に、password/auth/login table が残らないことを確認
* `mvn clean verify` が通ることを確認

新規 test を追加する場合は、以下を優先してください。

* 残存 schema に `account.password`, `subscriber.auth_password`, `login_rules` が存在しないこと
* 認証不要 feed の取り込み処理が壊れていないこと
* batch がメール送信 phase を呼ばないこと

## 検証コマンド

最低限、以下を Docker Compose 経由で実行してください。

```sh
docker compose config
docker compose down -v
docker compose up -d mysql
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
```

MySQL container 内の client で schema を確認してください。

```sh
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_name FROM information_schema.tables WHERE table_schema = 'sreader' ORDER BY table_name;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'sreader' AND (column_name LIKE '%password%' OR column_name LIKE 'auth_%' OR table_name = 'login_rules') ORDER BY table_name, column_name;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreader.flyway_schema_history ORDER BY installed_rank;"
```

`sreadertest` も migration 対象になっている場合は、同様に確認してください。

```sh
docker compose run --rm flyway -url=jdbc:mysql://mysql:3306/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm flyway -url=jdbc:mysql://mysql:3306/sreadertest -user=sreadertest -password=sreadertest migrate
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'sreadertest' AND (column_name LIKE '%password%' OR column_name LIKE 'auth_%' OR table_name = 'login_rules') ORDER BY table_name, column_name;"
```

最後に Maven 検証を実行してください。

```sh
docker compose run --rm maven mvn clean verify
```

## 期待する完了状態

作業完了時点で、以下を満たしてください。

* Gmail / SMTP / メール送信機能がコードから削除されている。
* Gmail account password を DB に保存しない。
* 購読先 login ID/password を DB に保存しない。
* `login_rules` table が現行 schema から消えている。
* Gmail 配信用 view / DAO / service が不要なら削除されている。
* メール送信済み管理だけに使う `publish_log` が不要なら削除されている。
* 認証付き取得機能がコードから削除されている。
* README / docs が新しい機能範囲に一致している。
* Flyway migration により既存開発 DB でも schema が移行される。
* Docker Compose 経由で Flyway と Maven 検証が実行できる。
* `mvn clean verify` が通る、または失敗する場合は原因と残課題が明確に記録されている。

## 今回やらないこと

以下は今回のスコープ外です。

* Gmail OAuth2 対応
* SMTP relay 対応
* 別メールサービスへの移行
* 外部サイトログイン情報の暗号化
* secret manager 導入
* Web UI 追加
* Spring Boot 化
* Hibernate 最新化
* Java language level 引き上げ
* DB schema の全面再設計
* RSS/Atom 処理全体の大規模リライト
* 本番運用設計

これらが必要に見えても、今回の作業では実装せず、docs の残課題に記録するだけにしてください。

## 成果物

最終的に、以下のような変更を含む patch を作ってください。

* Gmail / SMTP / メール送信関連 Java code の削除・修正
* 外部サイトログイン関連 Java code の削除・修正
* 不要 entity / DAO / service / test の削除・修正
* 不要 Maven dependency の削除
* Flyway migration 追加
* seed data から Gmail / login 関連情報を排除
* README 更新
* `docs/modernization.md` 更新
* 必要に応じて legacy SQL へのコメント追加または削除
* 必要に応じて test fixture 更新

## 最終報告フォーマット

作業完了後、以下の形式で報告してください。

1. 変更概要
2. 削除した Gmail / メール送信関連要素
3. 削除した購読先ログイン関連要素
4. 残した機能範囲
5. DB schema / Flyway migration の変更
6. 削除・変更した Maven dependency
7. 更新した docs / README
8. 実行した Docker / Flyway / Maven コマンド
9. 成功した検証
10. 失敗した検証があれば、その原因
11. 判断に迷った点と採用した判断
12. 残課題
13. 次の推奨ステップ

この作業では、認証情報を安全に保存する方向ではなく、認証情報を扱う機能そのものを削除する方向で進めてください。
