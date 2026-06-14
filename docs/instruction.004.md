あなたは Java / Maven / Hibernate / Flyway / PostgreSQL / Docker に詳しいソフトウェア移行エンジニアです。

対象リポジトリは `sasasin/sreader` です。このソフトウェアは約13年間更新停止していましたが、現在は現代化作業を進行中です。

これまでに以下の現代化作業が master に入っています。

* Docker Compose ベースの開発環境
* Maven multi-module 化
* 依存バージョン固定
* MySQL 8.4 ベースの Docker DB
* Flyway 導入
* MySQL init SQL と Flyway migration の分離
* Gmail メール配信機能の削除
* 購読先ログイン ID/password と login rule による認証付き取得機能の削除
* 公開 RSS/Atom feed 取得・本文抽出・DB 保存を中心にした構成への整理

今回の作業では、これまで MySQL を前提としていた DB 基盤を、PostgreSQL 16.x で動作するように改めてください。

## 今回の目的

MySQL を現代化後の標準 DB から外し、PostgreSQL 16.x を標準 DB にしてください。

具体的には、以下を達成してください。

1. Docker Compose の DB service を MySQL から PostgreSQL 16.x に置き換える。
2. Flyway migration SQL を PostgreSQL 16.x で実行できる形に変換する。
3. Java / Hibernate / JDBC 設定を PostgreSQL 用に変更する。
4. Maven dependency から MySQL Connector/J を外し、PostgreSQL JDBC Driver を追加する。
5. テスト・検証・ドキュメントを PostgreSQL 前提に更新する。
6. ホスト OS に PostgreSQL client / Java / Maven / Flyway をインストールせず、すべて Docker Compose 経由で検証できるようにする。

## 最重要制約

* ホスト OS に `mvn`, `java`, `javac`, `mysql`, `psql`, `flyway` をインストールしないでください。
* ホスト側で直接実行してよいのは、原則として `git`, `docker`, `docker compose`, 基本的なファイル確認コマンドだけです。
* Maven、Java、PostgreSQL client、Flyway は必ず Docker Compose 経由で実行してください。
* PostgreSQL は 16.x を使ってください。`latest`、PostgreSQL 17、PostgreSQL 18 にはしないでください。
* Docker image tag は可能なら `postgres:16.x` の具体的な patch version に固定してください。固定 tag が不明な場合は、調査して利用可能な 16.x tag を選んでください。
* MySQL 互換を維持する必要はありません。現代化後の標準 DB は PostgreSQL 16.x としてください。
* ただし、legacy MySQL scripts を削除するかどうかは慎重に判断してください。残す場合は legacy-only と明記してください。
* Gmail 配信機能や認証付き取得機能を復活させないでください。
* Hibernate の最新版移行、Spring Boot 化、全面リライトは今回のスコープ外です。
* 破壊的な Git 操作、履歴改変、force push はしないでください。

## まず現況を調査してください

Codex は、このリポジトリの全容をまだ把握していない前提で進めてください。

最初に以下を確認してください。

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
* `commons/src/main/resources`
* `batch/src/main/resources`
* `commons/src/main/java`
* `batch/src/main/java`
* `commons/src/test`
* `batch/src/test`
* shell scripts under `batch/` and root

特に以下の検索を実行してください。

```sh id="6z7n72"
git grep -n -E "mysql|MySQL|com.mysql|mysql-connector|jdbc:mysql|MySQLDialect|InnoDB|utf8mb4|AUTO_INCREMENT|ENGINE=|COLLATE|CHARSET|sreadertest|sreaderroot|allowPublicKeyRetrieval|useSSL|serverTimezone|docker/mysql|mysql-data|mysql:"
```

さらに PostgreSQL 化後の設定漏れを防ぐため、以下も検索してください。

```sh id="2c4h33"
git grep -n -E "hibernate.dialect|hibernate.connection.driver_class|hibernate.connection.url|hibernate.connection.username|hibernate.connection.password|DriverManager|DataSource|Flyway|flyway|jdbc:"
```

調査後、以下を分類してください。

1. Docker Compose / `.env.example` の MySQL 依存
2. Flyway migration SQL の MySQL 方言依存
3. Java / Hibernate / JDBC 設定の MySQL 依存
4. Maven dependency の MySQL 依存
5. テスト fixture / テスト DB 接続の MySQL 依存
6. README / docs / shell script の MySQL 依存
7. legacy として残してよいもの

分類結果を短くまとめてから変更に着手してください。

## Docker Compose 方針

`docker-compose.yml` を PostgreSQL 16.x 前提に変更してください。

### 期待する service 構成

* `postgres` service
* `flyway` service
* `maven` service

既存の `mysql` service は標準構成から外してください。必要なら legacy reference としてコメントや docs に残しても構いませんが、標準手順では使わないようにしてください。

### PostgreSQL service 要件

例:

```yaml id="9dnwzw"
postgres:
  image: ${POSTGRES_IMAGE:-postgres:16}
  environment:
    POSTGRES_DB: ${POSTGRES_DB:-sreader}
    POSTGRES_USER: ${POSTGRES_USER:-sreader}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-sreader}
  ports:
    - "${POSTGRES_PORT:-5432}:5432"
  volumes:
    - postgres-data:/var/lib/postgresql/data
    - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-sreader} -d ${POSTGRES_DB:-sreader}"]
    interval: 5s
    timeout: 5s
    retries: 12
```

実際の構成は既存 Compose に合わせて調整してください。

### DB / role 初期化

PostgreSQL official image の `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` だけでは primary database しか作られないため、`sreadertest` も必要なら init script を用意してください。

例:

```text id="68l77f"
docker/postgres/init/
  00-create-test-database.sh
```

または SQL と shell を組み合わせてください。

注意:

* PostgreSQL には `CREATE DATABASE IF NOT EXISTS` がありません。
* `CREATE DATABASE` は transaction block 内で実行できません。
* Docker entrypoint init scripts は volume 初期化時だけ実行されます。
* 既存 volume がある場合、init script は再実行されません。
* role / database 作成は Docker init の責務です。
* table / index / seed data は Flyway migration の責務です。

`sreader` と `sreadertest` の両方に migration を適用できる構成にしてください。

## .env.example 方針

`.env.example` を PostgreSQL 用に更新してください。

例:

```env id="d6fag7"
POSTGRES_IMAGE=postgres:16
POSTGRES_DB=sreader
POSTGRES_USER=sreader
POSTGRES_PASSWORD=sreader
POSTGRES_TEST_DB=sreadertest
POSTGRES_TEST_USER=sreadertest
POSTGRES_TEST_PASSWORD=sreadertest
POSTGRES_PORT=5432
FLYWAY_IMAGE=redgate/flyway:12.8.1
```

既存の `MYSQL_*` 変数は標準構成から削除してください。legacy 説明が必要なら docs に移してください。

## Flyway 方針

Flyway は PostgreSQL 用 JDBC URL に変更してください。

例:

```yaml id="1nlzsy"
flyway:
  image: ${FLYWAY_IMAGE:-redgate/flyway:12.8.1}
  depends_on:
    postgres:
      condition: service_healthy
  volumes:
    - ./db/migration:/flyway/migrations:ro
  environment:
    FLYWAY_URL: "jdbc:postgresql://postgres:5432/${POSTGRES_DB:-sreader}"
    FLYWAY_USER: ${POSTGRES_USER:-sreader}
    FLYWAY_PASSWORD: ${POSTGRES_PASSWORD:-sreader}
    FLYWAY_LOCATIONS: filesystem:/flyway/migrations
```

`sreadertest` へは override 引数で migrate できるようにしてください。

例:

```sh id="14smql"
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest migrate
```

## Flyway migration SQL の PostgreSQL 化

既存の `db/migration/` を調査してください。

MySQL 方言を PostgreSQL 方言へ変換してください。

### 変換対象の例

以下のような MySQL 固有構文を削除・変換してください。

* `ENGINE=InnoDB`
* `DEFAULT CHARSET=utf8mb4`
* `COLLATE=utf8mb4_0900_ai_ci`
* `CREATE TABLE ... LIKE ...`
* `USE sreader`
* `CREATE DATABASE ... CHARACTER SET ...`
* backtick quote
* `AUTO_INCREMENT`
* `TINYINT`
* `DATETIME` の MySQL 固有挙動
* `TEXT` / `VARCHAR` の長さ・index 制約差分
* `DROP VIEW IF EXISTS` / `DROP TABLE IF EXISTS` の順序
* MySQL だけで通る `ALTER TABLE` 構文
* MySQL 固有関数

### PostgreSQL 側の方針

* 接続先 database に対して migration を適用する前提にしてください。
* migration 内で `CREATE DATABASE` や `CREATE ROLE` はしないでください。
* `public` schema を使うなら、その前提を docs に明記してください。
* 必要なら `CREATE SCHEMA IF NOT EXISTS public;` は入れても構いませんが、通常は不要です。
* id が既存コードで `String` 前提なら、まずは `char(32)` / `varchar(32)` のまま維持して構いません。
* `text`, `varchar`, `date`, `timestamp` など PostgreSQL 標準型へ寄せてください。
* Boolean にできる箇所があっても、既存 Java mapping への影響が大きい場合は無理に変えないでください。
* schema の意味変更は避け、DBMS 方言変換を優先してください。

### 既存 migration を編集するか、新規 migration にするか

今回は DBMS を MySQL から PostgreSQL に切り替える作業です。MySQL 用 migration は PostgreSQL では実行できない可能性が高いため、以下のどちらかを選んでください。

#### 推奨: 既存 migration を PostgreSQL 用に置き換える

開発中の現代化ブランチであり、標準 DB を PostgreSQL に切り替えるため、`db/migration/V1__...`, `V2__...`, `V3__...` を PostgreSQL 用に編集して構いません。

ただし、docs に以下を明記してください。

* この変更により、既存の MySQL Flyway volume / schema history との互換性は標準サポート外になる。
* PostgreSQL 化後は fresh PostgreSQL database に対して migration を適用する。
* 既存 MySQL 開発データを自動移行する作業は今回のスコープ外。

#### 代替: DBMS 別 migration directory に分ける

必要なら以下のように分けても構いません。

```text id="6ga57n"
db/migration/postgresql/
db/migration/mysql-legacy/
```

この場合、Flyway の標準 location は PostgreSQL 側にしてください。MySQL 側は legacy reference にしてください。

どちらを選んでも、最終的に `docker compose run --rm flyway migrate` が PostgreSQL に対して成功することを優先してください。

## Java / Hibernate 設定の PostgreSQL 化

Hibernate / JDBC 設定を調査して、MySQL 前提を PostgreSQL に変更してください。

### 変更候補

* JDBC URL

  * before: `jdbc:mysql://...`
  * after: `jdbc:postgresql://postgres:5432/sreader`
* JDBC driver

  * before: `com.mysql.cj.jdbc.Driver` または `com.mysql.jdbc.Driver`
  * after: `org.postgresql.Driver`
* Hibernate dialect

  * before: MySQL dialect
  * after: Hibernate 現行バージョンで利用可能な PostgreSQL dialect

Hibernate 4.x を使っている場合は、存在する dialect class を確認してください。例として `org.hibernate.dialect.PostgreSQL82Dialect` や `org.hibernate.dialect.PostgreSQL9Dialect` が使える可能性があります。実際に使える class は現在の Hibernate version と依存 jar で確認してください。

### 注意

* Hibernate の本格アップグレードは今回のスコープ外です。
* dialect class が古く見えても、Hibernate 4.x の範囲で動作する PostgreSQL dialect を選んでください。
* code の大規模リファクタリングは避けてください。
* PostgreSQL で予約語に当たる table / column があれば、まず名前変更より mapping / quote の影響を調査してください。
* DB schema 名を Java code にハードコードしないでください。

## Maven dependency 方針

POM を確認し、MySQL Connector/J を削除して PostgreSQL JDBC Driver を追加してください。

例:

```xml id="9xoqdc"
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <version>...</version>
</dependency>
```

バージョンは現在の Maven Central を確認し、Java version との互換性を満たすものを固定してください。

注意:

* `mysql:mysql-connector-java`
* `com.mysql:mysql-connector-j`

上記が残っていれば、標準 dependency から削除してください。

Flyway Maven plugin を使っている場合は PostgreSQL support module が必要か確認してください。ただし、現在の構成が Flyway CLI container 方式なら、Maven plugin を無理に追加しないでください。

## テスト方針

既存 test を PostgreSQL 前提で通してください。

特に以下を確認してください。

* DAO test
* Hibernate mapping test
* Flyway migration 適用後の schema
* batch の起動・主要処理
* test DB `sreadertest` への migration
* MySQL 固有 SQL に依存する test fixture

DBUnit などで MySQL 前提の dataset / connection 設定がある場合は PostgreSQL 向けに修正してください。

可能なら、以下を検証する test または verification command を追加してください。

* `sreader` に Flyway migration が適用される。
* `sreadertest` に Flyway migration が適用される。
* 残存 schema に MySQL 固有の table option は存在しない。
* Java code に `jdbc:mysql` や `MySQLDialect` が残っていない。
* Maven dependency に MySQL connector が残っていない。

## legacy MySQL ファイルの扱い

以下を確認してください。

* `docker/mysql/init/`
* `commons/script/ddl.mysql.users.sql`
* `commons/script/ddl.mysql.tables.sql`
* `commons/script/dml.sql`
* `commons/script/gmail.sql`
* MySQL 前提の docs / README

方針:

* 現代化後の標準手順から MySQL を外してください。
* legacy reference として残す場合は、ファイルまたは docs に明確に legacy-only と書いてください。
* 標準 setup / test command に `mysql`, `jdbc:mysql`, `docker compose up -d mysql` が残らないようにしてください。
* 削除して問題ないと判断した MySQL Docker init ファイルは削除して構いません。
* ただし、13年前の履歴資料として価値がある legacy SQL は、無理に削除せず legacy と明記してください。

## README / docs 更新

`README.md` と `docs/modernization.md` を PostgreSQL 16.x 前提に更新してください。

必ず記録してください。

* 標準 DB を MySQL から PostgreSQL 16.x に変更したこと
* Docker Compose の標準 service 名が `postgres` になったこと
* ホスト OS に PostgreSQL client / Maven / Java / Flyway を入れないこと
* Flyway migration の実行方法
* `sreader` と `sreadertest` の migration 方法
* 既存 MySQL volume / schema は標準サポート外であること
* fresh PostgreSQL database を作るには `docker compose down -v` が必要な場合があること
* `docker compose down -v` はローカル開発 DB を破棄するため注意が必要であること
* MySQL 用 legacy scripts は現代化後の標準手順では使わないこと
* Gmail 配信機能と認証付き取得機能は復活していないこと

README に古い MySQL セットアップ手順が残っている場合は、legacy と明示するか、現代化後の標準手順から外してください。

## 検証コマンド

最低限、以下を Docker Compose 経由で実行してください。

```sh id="zz6mr1"
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
```

PostgreSQL の疎通確認:

```sh id="05rubn"
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
docker compose exec postgres psql -U sreader -d sreader -c "\l"
```

Flyway migration:

```sh id="bs1svv"
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
```

test DB migration:

```sh id="3k5lf7"
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
```

schema 確認:

```sh id="7ogz8i"
docker compose exec postgres psql -U sreader -d sreader -c "\dt"
docker compose exec postgres psql -U sreader -d sreader -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec postgres psql -U sreadertest -d sreadertest -c "\dt"
docker compose exec postgres psql -U sreadertest -d sreadertest -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Maven 検証:

```sh id="0h9zgj"
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean verify
```

MySQL 依存残存チェック:

```sh id="29zbrn"
git grep -n -E "mysql|MySQL|com.mysql|mysql-connector|jdbc:mysql|MySQLDialect|InnoDB|utf8mb4|allowPublicKeyRetrieval|serverTimezone" || true
```

この検索で legacy-only ファイルがヒットする場合は問題ありませんが、標準 runtime / test / docs の手順に残っている場合は修正してください。

## 期待する完了状態

作業完了時点で、以下を満たしてください。

* `docker compose up -d postgres` で PostgreSQL 16.x が起動する。
* `docker compose run --rm flyway migrate` が `sreader` に対して成功する。
* `sreadertest` にも Flyway migration を適用できる。
* `docker compose run --rm maven mvn clean verify` が成功する、または失敗する場合は原因が明確に記録されている。
* Java / Hibernate の標準接続先が PostgreSQL になっている。
* Maven dependency から MySQL Connector/J が消えている。
* PostgreSQL JDBC Driver が追加されている。
* Flyway migration が PostgreSQL 16.x で実行可能である。
* README / docs の標準手順が PostgreSQL 前提になっている。
* 標準手順に `mysql` service、`mysql` command、`jdbc:mysql` が残っていない。
* Gmail 配信機能と認証付き取得機能が復活していない。

## 今回やらないこと

以下は今回のスコープ外です。

* MySQL から PostgreSQL への実データ移行ツール作成
* 既存 MySQL Docker volume の自動変換
* 本番 DB migration 設計
* PostgreSQL tuning
* connection pool 導入
* Hibernate 最新化
* Spring Boot 化
* Java language level 引き上げ
* RSS/Atom 処理の大規模リライト
* Gmail 配信機能の復活
* 認証付き取得機能の復活
* secret manager 導入

必要に見えても、今回は docs の残課題に記録するだけにしてください。

## 成果物

最終的に、以下のような変更を含む patch を作ってください。

* `docker-compose.yml` の PostgreSQL 化
* `.env.example` の PostgreSQL 化
* `docker/postgres/init/` の追加
* 不要になった `docker/mysql/init/` の削除または legacy 化
* `db/migration/` の PostgreSQL 化
* Maven POM の JDBC dependency 更新
* Hibernate / JDBC 設定の PostgreSQL 化
* test / fixture の PostgreSQL 化
* README 更新
* `docs/modernization.md` 更新
* 必要に応じて shell script 更新
* 必要に応じて legacy MySQL SQL へのコメント追加

## 最終報告フォーマット

作業完了後、以下の形式で報告してください。

1. 変更概要
2. PostgreSQL 化した Docker Compose service
3. DB / role 初期化方式
4. Flyway migration の変更内容
5. Java / Hibernate / JDBC 設定の変更内容
6. Maven dependency の変更内容
7. MySQL 依存を残した箇所と、その理由
8. 更新した README / docs
9. 実行した Docker / Flyway / Maven / psql コマンド
10. 成功した検証
11. 失敗した検証があれば、その原因
12. 既存 MySQL 開発 DB / volume との互換性に関する注意
13. 残課題
14. 次の推奨ステップ

この作業では、MySQL と PostgreSQL の二重対応ではなく、現代化後の標準 DB を PostgreSQL 16.x に切り替えることを最優先してください。
