あなたは Java / Spring Boot / Maven / Docker / PostgreSQL / jOOQ / Flyway に詳しいソフトウェア保守エンジニアです。

対象リポジトリは `sasasin/sreader` です。このソフトウェアは約13年間更新停止していましたが、現在は現代化作業を進行中です。

直近の改修で、Java 25 + Spring Boot + jOOQ + Flyway + PostgreSQL 16.x の `app` module による新アプリケーションへリライト済みです。旧来の `batch` と `commons` は legacy reference として残っていますが、Spring Boot 版アプリケーションコードからは参照されていない想定です。

今回の作業では、旧来の `batch/` と `commons/` を安全に削除してください。

## 今回の目的

以下を実施してください。

1. `batch/` directory を削除する。
2. `commons/` directory を削除する。
3. Spring Boot 版 `app` module、Maven build、Docker Compose、Flyway、jOOQ、test、docs が削除後も正常に動くことを確認する。
4. legacy `batch` / `commons` への参照が残っていれば削除または修正する。
5. README / docs から「legacy reference として残っている」という説明を削除し、旧 module は削除済みであることを明記する。
6. 削除が安全だったことを確認手順と検証結果で示す。

## 最重要制約

* 目的は旧 legacy module の削除です。Spring Boot 版アプリの仕様変更や大規模リファクタリングはしないでください。
* Gmail 配信機能、SMTP 機能、認証付き取得機能を復活させないでください。
* Hibernate / DAO / XML mapping / MySQL 依存を復活させないでください。
* ホスト OS に `mvn`, `java`, `javac`, `psql`, `flyway` をインストールしないでください。
* ホスト側で直接実行してよいのは、原則として `git`, `docker`, `docker compose`, 基本的なファイル確認コマンドだけです。
* Maven、Java、PostgreSQL client、Flyway、jOOQ code generation は Docker Compose 経由で実行してください。
* 破壊的な Git 操作、履歴改変、force push はしないでください。

## まず現況を調査してください

Codex は、このリポジトリの現況や全容をまだ把握していない前提で進めてください。

最初に以下を確認してください。

* `README.md`
* `docs/`
* `pom.xml`
* `app/pom.xml`
* `app/src/main/java`
* `app/src/main/resources`
* `app/src/test`
* `db/migration/`
* `docker-compose.yml`
* `docker/`
* `.env.example`
* `.github/`
* `batch/`
* `commons/`
* `build_all.sh`
* その他 root 直下の shell script

## 削除前の安全確認

削除前に、`batch` と `commons` が標準 build/runtime から本当に参照されていないことを確認してください。

最低限、以下の検索を実行してください。

```sh id="u1s9xg"
git grep -n -E "commons|batch|build_all|run_feedreader|net\.sasasin\.sreader\.commons|net\.sasasin\.sreader\.batch|ddl\.mysql|dml\.sql|gmail\.sql|hibernate|Hibernate|hbm\.xml|jdbc:mysql|mysql-connector|login_rules|auth_password|SMTP|Gmail|gmail" -- ':!batch/**' ':!commons/**'
```

この検索では、`batch/` と `commons/` 自身を除いた現行コード・docs・設定からの参照を確認してください。

加えて、Maven module 参照を確認してください。

```sh id="g0lhtk"
grep -RInE "<module>.*(batch|commons)|<artifactId>(batch|commons)|../commons|../batch|commons/|batch/" pom.xml app/pom.xml .github docker docker-compose.yml README.md docs .env.example 2>/dev/null || true
```

必要なら以下も確認してください。

```sh id="5lxlrp"
find . -maxdepth 3 -type f \( -name "*.sh" -o -name "*.yml" -o -name "*.yaml" -o -name "*.xml" -o -name "*.md" -o -name "*.properties" \) -print \
  | grep -v '^\./batch/' \
  | grep -v '^\./commons/' \
  | xargs grep -nE "batch|commons|run_feedreader|build_all|hibernate|mysql|ddl\.mysql|gmail\.sql" || true
```

確認観点:

* root `pom.xml` の modules に `batch` / `commons` が含まれていないこと
* `app/pom.xml` が `commons` / `batch` artifact に依存していないこと
* Dockerfile / Compose が `batch` / `commons` を copy / mount / execute していないこと
* README / docs の標準手順が `batch` / `commons` に依存していないこと
* GitHub Actions が `batch` / `commons` に依存していないこと
* `build_all.sh` が legacy module だけを対象にしているなら削除対象に含めること
* `batch/script/run_feedreader.sh` のような旧 cron script が残っていれば削除対象に含めること
* `commons/script/*.sql` が標準 DB migration から外れていること
* `app` module が旧 package を import していないこと

確認結果を短くまとめてから削除に着手してください。

## 削除対象

基本的に以下を削除してください。

```text id="d41vi4"
batch/
commons/
```

以下も旧 `batch` / `commons` 専用であれば削除してください。

```text id="n4l2v0"
build_all.sh
```

ただし、`build_all.sh` が現在の Spring Boot 版 build に必要な wrapper として再利用されている場合は削除せず、内容を確認して判断してください。迷う場合は、標準手順が `docker compose run --rm maven mvn clean verify` であることを優先し、旧 shell script は削除してください。

## 削除後に修正するもの

削除後、以下を確認して必要に応じて更新してください。

### README

README から以下のような説明を削除または修正してください。

* `commons/`、`batch/` は legacy reference として残っている
* 旧 Hibernate entity / DAO が残っている
* legacy batch shell script が残っている
* 旧 module を参照する説明
* `batch/script/run_feedreader.sh`
* `build_all.sh`

代わりに、以下を明記してください。

* 現在の標準アプリケーションは `app/` module の Spring Boot アプリである
* 旧 `batch/` と `commons/` は削除済みである
* 旧 cron script 方式は削除済みであり、Spring Scheduler が標準である
* 旧 Hibernate / MySQL / Gmail / login 機能は復活していない
* Docker Compose 経由で build / test / app 起動を行う

### docs

`docs/` 配下を確認し、`batch` / `commons` を legacy reference として残している記述を修正してください。

ただし、歴史的経緯として言及するだけなら残しても構いません。その場合は、現在のソースツリーには存在しないことを明記してください。

例:

```text id="z4hye7"
旧 `batch/` と `commons/` module は Spring Boot 版への移行後に削除済みです。
```

### Maven

root `pom.xml` と `app/pom.xml` を確認してください。

* root modules は `app` のみであること
* `commons` / `batch` artifact への依存がないこと
* Hibernate / MySQL connector / JavaMail / legacy dependency が復活していないこと
* jOOQ / Flyway / PostgreSQL / Spring Boot の構成を壊していないこと

### Docker / Compose

`docker-compose.yml` と `docker/` 配下を確認してください。

* app build context が削除対象に依存していないこと
* Dockerfile が `batch` / `commons` を copy していないこと
* Maven service が旧 module を前提にしていないこと
* PostgreSQL / Flyway / app 起動手順が削除後も成立すること

### CI

`.github/` があれば確認してください。

* workflow が `batch` / `commons` に依存していないこと
* build command が新構成に一致していること
* CI がある場合は必要に応じて docs と整合させること

## 削除後の残存参照チェック

削除後、以下を実行してください。

```sh id="xpqi8e"
git grep -n -E "commons|batch|build_all|run_feedreader|net\.sasasin\.sreader\.commons|net\.sasasin\.sreader\.batch|ddl\.mysql|dml\.sql|gmail\.sql|hbm\.xml|jdbc:mysql|mysql-connector|login_rules|auth_password|SMTP|Gmail|gmail" || true
```

この結果を確認し、次のように分類してください。

1. 削除すべき残存参照
2. 歴史的説明として残してよい docs 記述
3. 誤検出

標準 build/runtime に関係する残存参照は必ず削除してください。

docs に残す場合も、「削除済み」「廃止済み」「legacy history」の文脈であることが分かるようにしてください。

## 検証コマンド

最低限、以下を Docker Compose 経由で実行してください。

```sh id="e329p4"
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
```

PostgreSQL 疎通確認:

```sh id="tv59oz"
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
```

Maven / Java 確認:

```sh id="kwbof1"
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
```

jOOQ / build / test:

```sh id="l93vb6"
docker compose run --rm maven mvn clean generate-sources
docker compose run --rm maven mvn clean verify
```

app image build:

```sh id="z8zs86"
docker compose build app
```

app 起動確認:

```sh id="lujcls"
docker compose up -d app
docker compose logs --tail=200 app
```

手動 job 実行 path が README に記載されている場合は、それも確認してください。

```sh id="gdjs18"
docker compose run --rm app java -jar /app/app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

最後に、再度残存参照を確認してください。

```sh id="sicb4o"
git grep -n -E "commons|batch|build_all|run_feedreader|net\.sasasin\.sreader\.commons|net\.sasasin\.sreader\.batch" || true
```

ヒットする場合は、標準 runtime / build に影響するものか、docs の historical note だけかを確認してください。

## 期待する完了状態

作業完了時点で、以下を満たしてください。

* `batch/` directory が削除されている。
* `commons/` directory が削除されている。
* 旧 module 専用なら `build_all.sh` も削除されている。
* root `pom.xml` の module は現在の Spring Boot app 構成だけである。
* `app` module が旧 `batch` / `commons` に依存していない。
* Docker Compose が削除後も動く。
* jOOQ code generation が成功する。
* `mvn clean verify` が成功する。
* app image build が成功する。
* app container が起動する。
* README / docs が削除後の構成に一致している。
* Gmail 配信、認証付き取得、Hibernate、MySQL 依存が復活していない。
* 標準 build/runtime path に `batch` / `commons` 参照が残っていない。

## 今回やらないこと

以下は今回のスコープ外です。

* Spring Boot アプリの機能追加
* feed reader の仕様変更
* DB schema の再設計
* jOOQ 設定の大幅変更
* Scheduler の設計変更
* Gmail / SMTP 機能の復活
* 認証付き取得機能の復活
* Hibernate の復活
* MySQL 対応の復活
* production deployment 設計

必要に見えても、今回の patch では実装せず、docs の残課題に記録するだけにしてください。

## 成果物

最終的に、以下のような変更を含む patch を作ってください。

* `batch/` の削除
* `commons/` の削除
* 必要なら `build_all.sh` の削除
* README 更新
* docs 更新
* 必要なら CI / Docker / Maven 設定の軽微な修正
* 削除後の残存参照修正

## 最終報告フォーマット

作業完了後、以下の形式で報告してください。

1. 変更概要
2. 削除した directory / file
3. 削除前に確認した参照チェック結果
4. 削除後に確認した残存参照チェック結果
5. Maven / Docker / app への影響確認
6. 更新した README / docs
7. 実行した Docker / Maven / PostgreSQL コマンド
8. 成功した検証
9. 失敗した検証があれば、その原因
10. 残した historical reference があれば、その理由
11. 残課題
12. 次の推奨ステップ

この作業では、旧 `batch` / `commons` を安全に削除し、Spring Boot 版 `app` module だけが標準 build/runtime path に残る状態にすることを最優先してください。
