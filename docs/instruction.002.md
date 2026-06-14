あなたは Java / Maven / MySQL / Docker / Flyway に詳しいソフトウェア移行エンジニアです。

対象リポジトリは `sasasin/sreader` です。このソフトウェアは約13年間更新停止していましたが、現在は現代化作業を進行中です。すでに第1段階として、Docker Compose ベースの開発環境、Maven multi-module 化、依存バージョン固定、MySQL 8.4 向けの初期化 SQL 整理が master にマージされています。

今回の作業は、計画案Cの一部だけに絞ります。

## 今回の目的

現在は `ddl.mysql.users.sql` や Docker Compose 初期化 SQL を `mysql` コマンドで実行し、DB ユーザー作成、DB 作成、テーブル作成、view 作成、seed data 投入を一体で行う構成になっています。

これを改めて、以下の構成にしてください。

1. アプリ用 DB ユーザー作成と database 作成は Docker Compose の MySQL 初期化用 SQL に分離する。
2. テーブル、view、seed data などの schema/data migration は Flyway で管理する。
3. `ddl.mysql.users.sql` を現代化手順の中で直接 `mysql` コマンド実行する作りをやめる。
4. ホスト OS に Maven / Java / MySQL client / Flyway をインストールせず、すべて Docker Compose 経由で実行できるようにする。

## 最重要制約

* ホスト OS に `mvn`, `java`, `javac`, `mysql`, `flyway` をインストールしないでください。
* ホスト側で直接実行してよいのは、原則として `git`, `docker`, `docker compose`, 基本的なファイル確認コマンドだけです。
* `mvn`, `java`, `mysql`, `flyway` は必ず Docker コンテナ内で実行してください。
* 実秘密情報をコミットしないでください。
* `.env.example` には開発用ダミー値だけを置いてください。
* 既存の legacy SQL は履歴として残して構いませんが、現代化後の標準手順からは外してください。
* Hibernate の本格移行、Gmail 認証刷新、DB スキーマ大規模再設計は今回のスコープ外です。
* 破壊的な Git 操作、履歴改変、force push はしないでください。

## まず現況を調査してください

作業前に以下を確認してください。

* `docker-compose.yml`
* `.env.example`
* `docs/modernization.md`
* root `pom.xml`
* `commons/pom.xml`
* `batch/pom.xml`
* `docker/mysql/init/`
* `commons/script/ddl.mysql.users.sql`
* `commons/script/ddl.mysql.tables.sql`
* `commons/script/dml.sql`
* `commons/script/gmail.sql`
* README のセットアップ手順
* 既存テストが MySQL 初期化 SQL に依存しているかどうか
* Hibernate 設定が接続先 database / user をどう扱っているか

調査後、短く作業方針をまとめてから変更に着手してください。

## 目標構成

### 1. Compose 初期化 SQL の役割

`docker/mysql/init/` 配下には、MySQL container 初回起動時に実行される SQL を置いてください。

この SQL の責務は以下だけに限定してください。

* `sreader` database の作成
* `sreadertest` database の作成
* 開発用 app user の作成
* 開発用 test user の作成
* 必要最小限の権限付与

この SQL には以下を含めないでください。

* `CREATE TABLE`
* `CREATE VIEW`
* `INSERT` による seed data 投入
* application schema の定義
* migration 対象の DDL
* Gmail や購読情報などの実データ

既存の `docker/mysql/init/01-sreader-schema.sql` が DB 作成、ユーザー作成、schema 作成、seed data 投入をまとめている場合は、役割ごとに分離してください。

例:

```text
docker/mysql/init/
  00-create-databases-and-users.sql
```

このファイルは例えば以下のような責務にしてください。

```sql
CREATE DATABASE IF NOT EXISTS sreader
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS sreadertest
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'sreader'@'%' IDENTIFIED BY 'sreader';
CREATE USER IF NOT EXISTS 'sreadertest'@'%' IDENTIFIED BY 'sreadertest';

GRANT ALL PRIVILEGES ON sreader.* TO 'sreader'@'%';
GRANT ALL PRIVILEGES ON sreadertest.* TO 'sreadertest'@'%';

-- テスト実行の都合で sreader user が sreadertest にもアクセスする必要があるなら、
-- 理由をコメントと docs に明記したうえで最小権限を付与してください。
```

実際の権限は既存テストとアプリの挙動を確認して、必要最小限にしてください。

### 2. Flyway migration の配置

Flyway migration は、リポジトリ内で分かりやすい場所に置いてください。

候補:

```text
db/migration/
  V1__create_schema.sql
  V2__insert_development_seed_data.sql
```

または Maven module に寄せるなら:

```text
commons/src/main/resources/db/migration/
  V1__create_schema.sql
  V2__insert_development_seed_data.sql
```

どちらを選んでもよいですが、選定理由を `docs/modernization.md` に記録してください。

### 3. Flyway migration の内容

`V1__create_schema.sql` には以下を含めてください。

* `account`
* `feed_url`
* `subscriber`
* `content_header`
* `content_full_text`
* `publish_log`
* `login_rules`
* `eft_rules`
* `content_view`

既存の `commons/script/ddl.mysql.tables.sql` と、現在の Docker 用 schema SQL を比較し、MySQL 8.4 / utf8mb4 で動く形にしてください。

注意点:

* migration 内で `CREATE DATABASE` や `CREATE USER` はしないでください。
* migration は接続先 database に対して適用される前提にしてください。
* 可能なら `sreader` と `sreadertest` で同じ migration set を使えるようにしてください。
* `content_view` は database 名を固定せず、接続先 database 内の table を参照する形にしてください。
* Flyway の versioned migration は原則として一度適用されたら変更しない前提なので、今後の変更追加を意識した構成にしてください。

`V2__insert_development_seed_data.sql` には、開発・テストに必要な dummy seed data のみを入れてください。

注意点:

* 実 Gmail アカウント、実パスワード、実購読情報を入れないでください。
* `gmail.sql` のような利用者が個別に編集すべきファイルは、Flyway migration にそのまま取り込まないでください。
* seed data がテスト専用か開発用かを docs に明記してください。

### 4. Flyway 実行方法

Flyway は Docker Compose 経由で実行できるようにしてください。

方法は以下のどちらかを選んで構いません。

#### 選択肢 A: Flyway 専用 Compose service

`docker-compose.yml` に `flyway` service を追加してください。

例:

```yaml
flyway:
  image: ${FLYWAY_IMAGE:-redgate/flyway:latest}
  depends_on:
    mysql:
      condition: service_healthy
  volumes:
    - ./db/migration:/flyway/sql:ro
  environment:
    FLYWAY_URL: jdbc:mysql://mysql:3306/sreader
    FLYWAY_USER: ${MYSQL_USER:-sreader}
    FLYWAY_PASSWORD: ${MYSQL_PASSWORD:-sreader}
```

実際の image、tag、設定方式は現在の Flyway 公式ドキュメントと動作確認結果に基づいて選んでください。`latest` を避けて固定 tag にできるなら固定してください。

`sreader` と `sreadertest` の両方に migrate できるようにしてください。

例:

```sh
docker compose run --rm flyway migrate
docker compose run --rm flyway -url=jdbc:mysql://mysql:3306/sreadertest -user=sreadertest -password=sreadertest migrate
```

#### 選択肢 B: Maven Flyway Plugin

root `pom.xml` に Flyway Maven Plugin を追加し、Maven container 内から実行できるようにしてください。

例:

```sh
docker compose run --rm maven mvn flyway:migrate
docker compose run --rm maven mvn -Dflyway.url=jdbc:mysql://mysql:3306/sreadertest -Dflyway.user=sreadertest -Dflyway.password=sreadertest flyway:migrate
```

この場合も、必要な Flyway MySQL support dependency があれば追加してください。

#### 選択基準

どちらの方式を選ぶかは任せますが、以下を満たしてください。

* ホストに Flyway を入れなくてよい。
* コマンドが README / docs から再現できる。
* `sreader` と `sreadertest` の両方に migration を適用できる。
* CI や将来の自動化に載せやすい。
* 既存の Docker Compose 構成と矛盾しない。

迷う場合は、Flyway 専用 Compose service を優先してください。DB migration は Maven build とは別責務として実行できる方が、今回の目的に合っています。

### 5. docker-compose.yml / .env.example の更新

必要に応じて、以下を更新してください。

* `docker-compose.yml`
* `.env.example`
* `.gitignore`

`.env.example` には、開発用 dummy 値として以下のような項目を含めてください。

```env
MYSQL_IMAGE=mysql:8.4
MYSQL_ROOT_PASSWORD=sreaderroot
MYSQL_DATABASE=sreader
MYSQL_USER=sreader
MYSQL_PASSWORD=sreader
MYSQL_TEST_DATABASE=sreadertest
MYSQL_TEST_USER=sreadertest
MYSQL_TEST_PASSWORD=sreadertest
MYSQL_PORT=3306
FLYWAY_IMAGE=...
```

実際の変数名は既存構成と整合させてください。

### 6. 古い SQL の扱い

`commons/script/ddl.mysql.users.sql` は、現代化後の標準手順では使わないようにしてください。

選択肢:

* ファイルは legacy reference として残す。
* ファイル先頭に「legacy only」「modern Docker/Flyway setup では使わない」旨のコメントを追加する。
* README / docs から直接実行手順を削除または非推奨化する。

ただし、削除する場合は影響を確認し、理由を docs に記録してください。迷う場合は削除せず、legacy reference として残してください。

### 7. README / docs の更新

`docs/modernization.md` を更新し、以下を記録してください。

* 今回の目的
* `ddl.mysql.users.sql` を直接実行する方式をやめたこと
* DB/user 作成は Compose 初期化 SQL の責務であること
* schema/data migration は Flyway の責務であること
* Flyway migration ファイルの場所
* `sreader` と `sreadertest` への migration 実行方法
* 初回セットアップ手順
* ローカル DB を作り直す場合の手順
* 既存 Docker volume がある場合、MySQL entrypoint の init SQL は再実行されないこと
* `docker compose down -v` はローカル開発 DB を破棄するため注意が必要であること
* 今回のスコープ外事項

README の古いセットアップ手順も、必要に応じて以下のように修正してください。

* JDK7 / Maven / MySQL をホストにインストールする旧手順は legacy と明示する。
* 現代化中の推奨手順は Docker Compose + Flyway であることを追記する。
* 詳細は `docs/modernization.md` に誘導する。

### 8. 検証コマンド

最低限、以下を実行してください。

```sh
docker compose config
docker compose down -v
docker compose up -d mysql
docker compose ps
```

Flyway 専用 service を採用した場合は、以下も実行してください。

```sh
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose run --rm flyway -url=jdbc:mysql://mysql:3306/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm flyway -url=jdbc:mysql://mysql:3306/sreadertest -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway -url=jdbc:mysql://mysql:3306/sreadertest -user=sreadertest -password=sreadertest info
```

Maven Flyway Plugin を採用した場合は、以下も実行してください。

```sh
docker compose run --rm maven mvn flyway:info
docker compose run --rm maven mvn flyway:migrate
docker compose run --rm maven mvn flyway:info
docker compose run --rm maven mvn -Dflyway.url=jdbc:mysql://mysql:3306/sreadertest -Dflyway.user=sreadertest -Dflyway.password=sreadertest flyway:info
docker compose run --rm maven mvn -Dflyway.url=jdbc:mysql://mysql:3306/sreadertest -Dflyway.user=sreadertest -Dflyway.password=sreadertest flyway:migrate
docker compose run --rm maven mvn -Dflyway.url=jdbc:mysql://mysql:3306/sreadertest -Dflyway.user=sreadertest -Dflyway.password=sreadertest flyway:info
```

さらに、MySQL container 内の client で以下を確認してください。

```sh
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema IN ('sreader', 'sreadertest') ORDER BY table_schema, table_name;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreader.flyway_schema_history;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT * FROM sreadertest.flyway_schema_history;"
```

最後に既存の Maven 検証も実行してください。

```sh
docker compose run --rm maven mvn clean verify
```

### 9. 期待する完了状態

作業完了時点で、以下を満たしてください。

* MySQL container 初回起動時には DB と user だけが作られる。
* テーブル、view、seed data は Flyway migration で作られる。
* `sreader.flyway_schema_history` が存在する。
* `sreadertest.flyway_schema_history` が存在する。
* `ddl.mysql.users.sql` は現代化後の標準手順から外れている。
* ホストに Maven / Java / MySQL client / Flyway を入れずに検証できる。
* `mvn clean verify` が通る、または失敗する場合は原因と残課題が明確に記録されている。

### 10. 今回やらないこと

以下は今回のスコープ外です。

* Hibernate 4 から新しい Hibernate への移行
* Java language level の引き上げ
* Spring Boot 化
* Gmail 認証方式の刷新
* 本番向け secret management の実装
* DB schema の大規模再設計
* 既存 application logic の仕様変更
* 全依存ライブラリの最新版追従

必要な場合は、docs の残課題に記録するだけにしてください。

## 成果物

最終的に、以下のような変更を含む patch を作ってください。

* `docker-compose.yml` の更新
* `.env.example` の更新
* `docker/mysql/init/00-create-databases-and-users.sql` などの追加・整理
* Flyway migration SQL の追加
* 必要なら root `pom.xml` の Flyway plugin 設定
* `docs/modernization.md` の更新
* 必要なら README の更新
* 必要なら `.gitignore` の更新

## 最終報告フォーマット

作業完了後、以下の形式で報告してください。

1. 変更概要
2. 追加・変更したファイル
3. Flyway 導入方式

   * Flyway 専用 Compose service か Maven plugin か
   * その方式を選んだ理由
4. Compose 初期化 SQL に残した責務
5. Flyway migration に移した責務
6. 実行したコマンド
7. 成功した検証
8. 失敗した検証があれば、その原因
9. 既存 `ddl.mysql.users.sql` の扱い
10. 残課題
11. 次の推奨ステップ

この作業では、アプリケーション機能の変更よりも、DB 初期化責務の分離と Flyway による再現可能な schema/data migration を最優先してください。
