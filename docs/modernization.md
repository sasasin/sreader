# モダナイゼーション: Docker Compose + Flyway

## 目的

このマイルストーンでは、古い Java/Maven/MySQL プロジェクトに対して、Docker だけで再現できる作業基盤を作ります。Maven、JDK、Java コマンド、MySQL client コマンド、Flyway は、ホスト OS ではなく Docker コンテナ内で実行する方針です。

今回の追加作業では、MySQL 初期化の責務を分離しました。Docker Compose の MySQL entrypoint が実行する SQL は database/user 作成だけを担当し、table、view、development seed data は Flyway migration で管理します。現代化後の標準手順では `commons/script/ddl.mysql.users.sql` を `mysql` コマンドで直接実行しません。

## リポジトリ構成

- `commons` には、共通 entity、DAO 実装、utility、legacy SQL script、および大半の test が含まれています。
- `batch` には、feed 取り込み、全文抽出、配信を行う command-line job が含まれています。
- `batch` は `commons` に依存しており、現在は Maven reactor module dependency として接続しています。
- `db/migration` には、Docker Compose の Flyway service から実行する migration SQL を置きます。
- `commons/script` 配下の元 SQL file は legacy reference として残しますが、現代化中の標準セットアップでは実行しません。

Flyway migration は root の `db/migration` に配置します。DB migration は Java artifact の resource ではなく開発環境と DB lifecycle の責務として扱いたいため、Maven module 配下ではなく Compose service から直接 mount しやすい場所を選びました。

## Docker Compose

MySQL を起動します。

```sh
docker compose up -d mysql
```

`sreader` database に Flyway migration を適用します。

```sh
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
```

`sreadertest` database に同じ Flyway migration を適用します。

```sh
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
```

Maven コンテナ内で Maven と Java を確認します。

```sh
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
```

reactor 全体を build/test します。test は `commons/src/test/resources/hibernate.cfg.xml` により `sreadertest` database へ接続するため、事前に `sreadertest` へ Flyway migration を適用してください。

```sh
docker compose run --rm maven mvn clean verify
```

MySQL コンテナ内の client で DB を確認します。

```sh
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema IN ('sreader', 'sreadertest') ORDER BY table_schema, table_name;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreader.flyway_schema_history;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreadertest.flyway_schema_history;"
```

Maven local repository は `maven-repository` Docker volume に保存します。MySQL data は `mysql-data` Docker volume に保存します。

## MySQL 初期化

Docker Compose は `docker/mysql/init/` を MySQL container の `/docker-entrypoint-initdb.d` に mount します。ここに置く SQL は MySQL container の初回起動時、つまり `mysql-data` volume が空の時だけ実行されます。

`docker/mysql/init/00-create-databases-and-users.sql` の責務は以下だけです。

- `sreader` database の作成
- `sreadertest` database の作成
- 開発用 `sreader` user の作成
- テスト用 `sreadertest` user の作成
- それぞれの database への最小限の権限付与

この初期化 SQL には `CREATE TABLE`、`CREATE VIEW`、seed data の `INSERT`、Gmail や購読情報などの利用者データを含めません。テストは `sreadertest` user で `sreadertest` database に接続するため、`sreader` user へ `sreadertest` の権限は付与していません。

既存 Docker volume がある場合、MySQL entrypoint の init SQL は再実行されません。DB/user 作成 SQL からやり直す必要がある場合は、ローカル開発 DB を破棄する操作であることを理解したうえで、次を実行してください。

```sh
docker compose down -v
docker compose up -d mysql
```

`docker compose down -v` は `mysql-data` volume を削除し、ローカルの開発 DB データを失います。

## Flyway migration

Flyway は専用 Compose service として定義しています。DB migration を Maven build と別責務にでき、CI や将来の自動化でも database ごとに同じ service を再利用しやすいためです。image は `redgate/flyway:12.8.1` に固定しています。

- `db/migration/V1__create_schema.sql` は `account`、`feed_url`、`subscriber`、`content_header`、`content_full_text`、`publish_log`、`login_rules`、`eft_rules`、`content_view` を作成します。
- `db/migration/V2__insert_development_seed_data.sql` は開発・テスト用の dummy seed data だけを投入します。

`V1` は `commons/script/ddl.mysql.tables.sql` と旧 Docker schema SQL を元に、MySQL 8.4 / `utf8mb4` で動く形へ整理しています。legacy SQL の `varchar(8096)` は `utf8mb4` の row-size limit を避けるため、旧 Docker schema と同じく `text` にしています。migration 内では `CREATE DATABASE` や `CREATE USER` を行わず、接続先 database に対して適用される前提です。

`V2` は dummy data だけです。`commons/script/gmail.sql` のような利用者が個別に編集すべき Gmail アカウント情報は取り込みません。

## Maven の変更

- root `pom.xml` を追加し、packaging `pom` として `commons` / `batch` module を定義しました。
- 依存 version と plugin version を parent POM 側に移しました。
- module POM から Maven version range を削除しました。
- legacy な `system` scope の `juniversalchardet` jar を、Maven Central の artifact `com.googlecode.juniversalchardet:juniversalchardet:1.0.3` に置き換えました。
- この codebase はもともと JDK 7 向けに作られているため、Java source/target は `1.7` のまま維持しています。Docker の Maven image は、ホストへ JDK を入れずに済むよう、より新しい JDK を使用します。
- compiler、resources、surefire、enforcer の plugin management を追加しました。
- Hibernate 4/Javassist が legacy な reflective proxy generation を使うため、Java 17 での test 実行用に Surefire の `--add-opens` option を追加しました。

## 依存バージョン固定方針

version は意図的に保守的に固定しています。目的は framework の全面 upgrade ではなく、再現可能な build 挙動を作ることです。

- 大きな ORM 移行を避けるため、Hibernate は `4.2.7.SP1` のまま維持しました。
- Docker の MySQL 8 service で現行 driver artifact を使えるよう、MySQL Connector/J は `com.mysql:mysql-connector-j:8.0.33` に移しました。
- HtmlUnit は modern API へ一気に上げず、既存 code の時代に近い `2.13` に固定しました。
- DBUnit、EasyMock、JUnit、POI などの test library は、現在の code と互換性のある明示 version に固定しました。
- このマイルストーンでは、Hibernate 4 から modern Hibernate への移行は行っていません。

## 残課題

- Hibernate 4 から modern Hibernate への移行は別作業として計画してください。
- Java language level の引き上げは別作業として計画してください。
- Spring Boot 化は今回の scope 外です。
- Gmail authentication modernization は別作業として計画してください。
- 本番向け secret management は今回の scope 外です。
- DB schema の大規模再設計は今回の scope 外です。
- `mvn clean verify` は Java 17 で obsolete な `source`/`target` value `1.7` に対する warning を出す可能性があります。
- DBUnit は MySQL 利用時に `DefaultDataTypeFactory` に関する warning を出す可能性があります。
- `slf4j-log4j12:1.7.36` は `slf4j-reload4j` に relocated されています。dependency behavior の影響範囲を広げないため、このマイルストーンでは変更していません。
- legacy な host-oriented shell script は、削除・書き換え・Docker Compose wrapper 化のいずれかを検討してください。

## 検証実行

以下の command を host から実行し、Maven、Java、MySQL client、Flyway の作業が Docker コンテナ内で行われることを確認します。

```sh
docker compose config
docker compose down -v
docker compose up -d mysql
docker compose ps
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema IN ('sreader', 'sreadertest') ORDER BY table_schema, table_name;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreader.flyway_schema_history;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreadertest.flyway_schema_history;"
docker compose run --rm maven mvn clean verify
```
