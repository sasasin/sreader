# モダナイゼーション 第1マイルストーン

## 目的

このマイルストーンでは、古い Java/Maven/MySQL プロジェクトに対して、Docker だけで再現できる作業基盤を作ります。Maven、JDK、Java コマンド、MySQL client コマンドは、ホスト OS ではなく Docker コンテナ内で実行する方針です。

## リポジトリ構成

- `commons` には、共通 entity、DAO 実装、utility、SQL script、および大半の test が含まれています。
- `batch` には、feed 取り込み、全文抽出、配信を行う command-line job が含まれています。
- `batch` は `commons` に依存しており、現在は Maven reactor module dependency として接続しています。
- 既存の shell script は残していますが、ホスト上で `mvn` や `java` を直接実行する手順は推奨しません。

## Docker Compose

MySQL を起動します。

```sh
docker compose up -d mysql
```

Maven コンテナ内で Maven と Java を確認します。

```sh
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
```

reactor 全体を build/test します。

```sh
docker compose run --rm maven mvn clean verify
```

MySQL コンテナ内の client で DB を確認します。

```sh
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
```

Maven local repository は `maven-repository` Docker volume に保存します。MySQL data は `mysql-data` Docker volume に保存します。

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

## MySQL 初期化

`commons/script/` 配下の元 SQL file は残しています。Docker Compose は `docker/mysql/init/` を mount し、そこに MySQL 8 向けの初期化 SQL を配置しています。

- legacy な `GRANT ... IDENTIFIED BY ...` の代わりに、`CREATE USER IF NOT EXISTS ... IDENTIFIED BY ...` を使います。
- `utf8mb4` と `utf8mb4_0900_ai_ci` を使います。
- `utf8mb4` では MySQL の row-size limit を超えやすいため、Docker schema では非常に長い URL/rule column を `varchar(8096)` から `text` に変換しています。
- `sreader` と `sreadertest` を別 database として作成します。
- seed data は開発用の dummy data のみにしています。

Docker 用 SQL は開発 baseline であり、本番向け secret 管理ではありません。

## 残課題

- `mvn clean verify` は成功しますが、Java 17 は obsolete な `source`/`target` value `1.7` に対する warning を出します。Java language level の移行を計画するまでは、このまま維持します。
- DBUnit は MySQL 利用時に `DefaultDataTypeFactory` に関する warning を出します。test は成功していますが、将来的には MySQL 専用の DBUnit factory を検討してください。
- `slf4j-log4j12:1.7.36` は `slf4j-reload4j` に relocated されています。dependency behavior の影響範囲を広げないため、第1マイルストーンでは変更していません。
- 一部の negative-path DAO test は、期待通りの例外 stack trace を出力しながら成功します。
- legacy test を MySQL 8 でそのまま実行し続けるべきか、test fixture/schema cleanup が必要かを確認してください。
- Compose 内で batch job を実行する場合、runtime Hibernate URL を `localhost` から Docker-aware な hostname へ移す必要があります。
- production runtime でも Docker 向けの `mysql` hostname を維持するか、Hibernate connection setting を外部化するかを検討してください。
- legacy な host-oriented shell script は、削除・書き換え・Docker Compose wrapper 化のいずれかを検討してください。
- Hibernate migration は別作業として計画してください。このマイルストーンでは意図的に scope 外です。
- Gmail authentication modernization は別作業として計画してください。
- 再現可能な baseline が安定したあとで、古い依存の security review と update を行ってください。

## 検証実行

以下の command を host から実行し、Maven、Java、MySQL client の作業が Docker コンテナ内で行われることを確認しました。

```sh
docker compose config
docker compose up -d mysql
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean verify
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
```

確認結果:

- Maven: Apache Maven 3.9.16.
- Java: Eclipse Temurin 17.0.19.
- Reactor modules: `sreader-parent`, `commons`, `batch`.
- Tests: `commons` で 15 tests を実行し、failure 0、error 0。`batch` には実行対象 test がありません。
- MySQL databases: `sreader` と `sreadertest` が存在します。
