あなたは Java / Spring Boot / jOOQ / Flyway / PostgreSQL / Docker に詳しいソフトウェアリライト担当エンジニアです。

対象リポジトリは `sasasin/sreader` です。このソフトウェアは約13年間更新停止していましたが、現在は段階的な現代化作業を進めています。

ここまでに、概ね以下の作業が行われています。

* Docker Compose ベースの開発環境
* Maven multi-module 化
* Flyway 導入
* PostgreSQL 16.x 化
* Gmail メール配信機能の削除
* 購読先ログイン ID/password と login rule による認証付き取得機能の削除
* 認証不要な公開 RSS/Atom feed 取得・本文抽出・DB 保存を中心にした機能範囲への整理

今回の作業では、「計画案 D: 中身だけ再利用して作り直す」リライトプランに取り組みます。

既存の古い Hibernate / DAO / shell script 中心の実装を、Java 25 + Spring Boot + jOOQ + Flyway + PostgreSQL 16 の Spring Boot アプリケーションとして作り直してください。

## 最重要方針

* 既存コードを小手先で直すのではなく、Spring Boot の典型的な作法に沿って再構成してください。
* ただし、SReader のドメイン知識、既存 schema、既存 batch の処理順序、RSS/Atom 取得・本文抽出ロジックは読み取って再利用してください。
* Hibernate は使わないでください。
* JPA / Spring Data JPA は使わないでください。
* DB アクセスは jOOQ を使ってください。
* DB migration は Flyway を使ってください。
* DB は PostgreSQL 16.x を標準としてください。
* Java は 25 をターゲットにしてください。
* Spring Boot の標準的な構成、設定、DI、configuration properties、logging、test の作法に従ってください。
* `batch/script/run_feedreader.sh` で行っていた定期実行は廃止し、Docker Compose 内のアプリケーションコンテナで Spring Scheduler として定期実行させてください。
* ホスト OS に `mvn`, `java`, `javac`, `psql`, `flyway` をインストールしないでください。ホスト側で直接実行してよいのは `git`, `docker`, `docker compose`, 基本的なファイル確認コマンドだけです。
* Gmail 配信機能と認証付き取得機能を復活させないでください。
* 実秘密情報をコミットしないでください。
* 破壊的な Git 操作、履歴改変、force push はしないでください。

## 今回のゴール

最終的に、以下の状態を目指してください。

1. Spring Boot アプリケーションとして起動できる。
2. Docker Compose で PostgreSQL 16.x とアプリケーションコンテナを起動できる。
3. Flyway migration がアプリ起動時または明示コマンドで適用できる。
4. jOOQ code generation により PostgreSQL schema から型安全な Java code を生成できる。
5. 認証不要な公開 RSS/Atom feed を取得できる。
6. feed entry から記事 URL とタイトルを保存できる。
7. 認証不要ページの本文抽出を行い、DB に保存できる。
8. 従来 `batch/script/run_feedreader.sh` で行っていた処理を Spring Scheduler が定期実行する。
9. 手動実行用の Spring Boot runner または actuator endpoint など、開発時に1回だけ feed 取得 job を実行できる仕組みを用意する。
10. `docker compose up app` でアプリケーションコンテナが起動し、スケジューラが動く。
11. `docker compose run --rm maven mvn clean verify` など、Docker 経由で build/test が通る。

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
* `docker/postgres/init/`
* `commons/src/main/java`
* `batch/src/main/java`
* `commons/src/test`
* `batch/src/test`
* `batch/script/run_feedreader.sh`
* `commons/script/`
* 既存の Hibernate mapping / DAO / entity
* RSS/Atom 取得ロジック
* feed URL 登録ロジック
* 本文抽出ロジック
* `eft_rules` の使われ方
* テスト fixture

特に以下を検索してください。

```sh id="r7xfrm"
git grep -n -E "Hibernate|hibernate|SessionFactory|Session|Criteria|DAO|Dao|Entity|hbm.xml|jpa|javax.persistence|jakarta.persistence|run_feedreader|SingleAccountFeedReader|ContentHeaderDriver|ContentFullTextDriver|Feed|RSS|Atom|ROME|SyndFeed|HtmlUnit|WebClient|eft_rules|content_header|content_full_text|feed_url|Scheduled|cron"
```

調査後、以下を分類してください。

1. 新 Spring Boot アプリへ移植するドメインロジック
2. 削除する legacy infrastructure
3. 削除する Hibernate / DAO / mapping
4. jOOQ repository/service として作り直す DB アクセス
5. Spring Scheduler に置き換える batch 実行
6. 残す Flyway migration
7. 更新が必要な Docker Compose / docs / tests

分類結果を短くまとめてから実装に着手してください。

## 推奨アーキテクチャ

Spring Boot の典型的な layered architecture にしてください。

推奨 package 構成:

```text id="c3l11x"
src/main/java/net/sasasin/sreader/
  SreaderApplication.java
  config/
    FeedReaderProperties.java
    SchedulingConfig.java
    JooqConfig.java       # 必要な場合のみ
  domain/
    FeedUrl.java
    ContentHeader.java
    ContentFullText.java
    ExtractRule.java
  repository/
    FeedUrlRepository.java
    ContentHeaderRepository.java
    ContentFullTextRepository.java
    ExtractRuleRepository.java
  service/
    FeedReaderService.java
    FeedRegistrationService.java
    FeedEntryImportService.java
    FullTextExtractionService.java
    ExtractRuleService.java
  scheduler/
    FeedReaderScheduler.java
  runner/
    FeedReaderCommandRunner.java  # 必要なら
  web/
    HealthController.java         # 必要なら最小限
```

ただし、実際の構成は Spring Boot の標準作法に沿って、過剰に細分化しすぎないでください。

## Maven / module 構成方針

既存の `commons` / `batch` module は、今回のリライトで残す必要があるか再評価してください。

推奨方針:

* ルート直下に Spring Boot アプリ module を作る、または既存 module を整理して単一 Spring Boot app に寄せる。
* 迷う場合は `app` module を新設してください。
* 新しい標準アプリは `app` module としてください。
* 既存 `commons` / `batch` は移行期間だけ legacy module として残すか、コンパイル対象から外すか、削除するかを判断してください。
* 新アプリが動くまでの比較対象として legacy code を残す場合は、docs に legacy-only と明記してください。
* 最終的に標準 build では Spring Boot app が主成果物になるようにしてください。

候補:

```text id="e7i8hh"
pom.xml
app/
  pom.xml
  src/main/java/...
  src/main/resources/...
  src/test/java/...
db/migration/
docker/
docker-compose.yml
docs/
```

Spring Boot Maven Plugin を導入し、container 内で build できるようにしてください。

Java 25 を明示してください。

例:

```xml id="ra82eg"
<java.version>25</java.version>
<maven.compiler.release>25</maven.compiler.release>
```

実際の Spring Boot / jOOQ / Flyway / PostgreSQL JDBC driver の version は、現在利用可能な安定版を確認して固定してください。`latest` 任せにしないでください。

## Spring Boot 依存関係方針

必要な starter / dependency を選んでください。

候補:

* `spring-boot-starter`
* `spring-boot-starter-jooq`
* `spring-boot-starter-flyway`
* `spring-boot-starter-validation`
* `spring-boot-starter-actuator`
* `spring-boot-starter-test`
* `org.postgresql:postgresql`
* `org.jooq:jooq-codegen-maven` plugin
* RSS/Atom parsing library
* HTML parsing / extraction library
* 必要なら HtmlUnit
* 必要なら jsoup

方針:

* JPA / Hibernate は入れないでください。
* `spring-boot-starter-data-jpa` は使わないでください。
* 旧 `slf4j-log4j12` は使わず、Spring Boot 標準 logging に寄せてください。
* 古い DBUnit / EasyMock / JUnit 4 依存は原則として新アプリには持ち込まないでください。
* test は JUnit Jupiter / Spring Boot Test / Testcontainers を優先してください。
* 依存は必要最小限にしてください。

## jOOQ 方針

DB access は jOOQ で実装してください。

### jOOQ code generation

Flyway migration を適用した PostgreSQL schema から jOOQ code を生成してください。

方針:

* Maven build 内で jOOQ code generation を実行できるようにする。
* Docker Compose の PostgreSQL を使って生成する方式、または Testcontainers を使って生成する方式を検討してください。
* ホストに PostgreSQL client を入れずに生成できるようにしてください。
* 生成先は `target/generated-sources/jooq` など Maven 標準に寄せてください。
* 生成コードを Git 管理するかどうかは判断してください。迷う場合は生成コードは Git 管理せず、build 時生成にしてください。
* `public` schema を対象にしてください。
* Flyway 管理テーブル `flyway_schema_history` は生成対象から除外してください。

### repository 実装

repository は jOOQ の `DSLContext` を constructor injection で受け取る形にしてください。

例:

```java id="eymhxu"
@Repository
public class FeedUrlRepository {
    private final DSLContext dsl;

    public FeedUrlRepository(DSLContext dsl) {
        this.dsl = dsl;
    }
}
```

Spring Boot が jOOQ `DSLContext` を auto-configure できる構成にしてください。必要な場合だけ明示 configuration を追加してください。

## Flyway / schema 方針

既存の `db/migration` を調査し、新アプリに必要な schema として整理してください。

現行機能に必要な table は概ね以下です。

* `feed_url`
* `content_header`
* `content_full_text`
* `eft_rules`

Gmail 配信や認証付き取得の table は復活させないでください。

不要な table:

* `account`
* `subscriber`
* `publish_log`
* `login_rules`
* Gmail 配信用 `content_view`

方針:

* Spring Boot app の Flyway auto migration を使えるようにしてください。
* 既存 migration を使うか、新規 schema baseline として整理するか判断してください。
* 今回はリライト作業なので、開発用 schema の migration squash を行っても構いません。
* ただし、判断理由を docs に明記してください。
* 本番データ移行は今回のスコープ外です。
* PostgreSQL 16.x に適した SQL にしてください。
* ID は UUID 型に変更しても構いません。ただし既存コード移植コストが上がる場合は `char(32)` / `varchar` を維持して構いません。判断理由を記録してください。
* `created_at`, `updated_at` を追加する場合は、用途を docs に書いてください。
* unique constraint / index は最低限整備してください。

推奨 schema 例:

```text id="vumrv4"
feed_url
  id
  url
  created_at
  updated_at

content_header
  id
  feed_url_id
  url
  title
  published_at
  created_at
  updated_at

content_full_text
  id
  content_header_id
  full_text
  extracted_at
  created_at
  updated_at

eft_rules
  id
  url_pattern
  extract_rule
  created_at
  updated_at
```

実際の column は既存ロジックと互換性を見て決めてください。

## RSS/Atom 取得方針

既存の RSS/Atom 取得処理を読み、必要な挙動を移植してください。

要件:

* 認証不要な公開 RSS/Atom feed URL を対象にする。
* feed URL は DB に保存されたものを読む。
* 開発用には設定ファイルまたは初期 seed から feed URL を登録できるようにしてもよい。
* feed entry ごとに記事 URL とタイトルを保存する。
* 既に保存済みの記事 URL は重複登録しない。
* 失敗した feed があっても、他の feed 処理が止まらないようにする。
* timeout、user-agent、retry の方針を application properties に切り出してください。
* 大規模な crawler にはしないでください。今回の範囲は既存 SReader 相当の lightweight feed reader です。

## 本文抽出方針

既存の本文抽出処理と `eft_rules` を調査し、移植してください。

要件:

* 認証不要ページのみ対象。
* 記事 URL から HTML を取得する。
* `eft_rules` が使えるなら、その設計を Spring service に移す。
* 抽出ルールに一致しない場合の fallback を用意するか、失敗として記録してください。
* full text は `content_full_text` に保存する。
* 同じ `content_header_id` に対して重複保存しない。
* HTML 取得失敗、parse 失敗、抽出失敗はログに出し、scheduler 全体を落とさない。
* 外部サイトログインや cookie 認証は実装しないでください。

## Spring Scheduler 方針

`batch/script/run_feedreader.sh` で行っていた定期実行は廃止し、アプリケーションコンテナ内の Spring Scheduler に移してください。

要件:

* `@EnableScheduling` を使う。
* `@Scheduled` の cron/fixedDelay は `application.yml` / environment variable で変更できるようにする。
* default は安全な間隔にしてください。例: 15分ごと、または 1時間ごと。
* scheduler の有効/無効を property で切り替えられるようにしてください。
* 開発時や test 時は scheduler を無効化できるようにしてください。
* job の多重起動を避けてください。単一インスタンス前提でよいですが、同一 JVM 内での重複実行は防いでください。
* scheduler は以下の順で処理してください。

  1. feed URL 一覧を読む
  2. RSS/Atom feed を取得する
  3. content header を upsert / insert する
  4. 未抽出 content の本文を取得・抽出する
  5. 結果を log する

`batch/script/run_feedreader.sh` は標準手順から外してください。残す場合は legacy-only と明記してください。可能なら削除してください。

## 手動実行方針

scheduler とは別に、開発・検証用に1回だけ feed reader job を実行できる手段を用意してください。

候補:

* Spring profile `job` で `CommandLineRunner` を起動
* `--sreader.job.run-once=true` property
* actuator endpoint
* Docker Compose command override

迷う場合は、以下のような property-driven `CommandLineRunner` を用意してください。

```text id="x34x0g"
java -jar app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

Docker Compose 経由でも実行できるようにしてください。

```sh id="x2vso1"
docker compose run --rm app java -jar /app/app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

実際の jar path は作成する Dockerfile に合わせて調整してください。

## Docker 方針

Docker Compose を Spring Boot app 対応に更新してください。

期待 service:

* `postgres`
* `flyway` または Spring Boot Flyway auto migration
* `maven`
* `app`

方針:

* PostgreSQL 16.x を使う。
* Java 25 runtime image を使う。
* Maven build も Java 25 で行う。
* app container は Spring Boot jar を実行する。
* app は postgres の healthcheck 後に起動する。
* app は environment variable から DB 接続情報と scheduler 設定を受け取る。
* production-like runtime container と development build container を分けてよい。
* ホストに Java/Maven を要求しない。

候補構成:

```text id="lxxmzz"
Dockerfile
docker-compose.yml
```

または:

```text id="7ghh0l"
docker/app/Dockerfile
docker/maven/Dockerfile
docker-compose.yml
```

必要に応じて multi-stage build にしてください。

## application.yml 方針

Spring Boot の典型的な設定にしてください。

例:

```yaml id="kw6mez"
spring:
  application:
    name: sreader
  datasource:
    url: ${SREADER_DATASOURCE_URL:jdbc:postgresql://postgres:5432/sreader}
    username: ${SREADER_DATASOURCE_USERNAME:sreader}
    password: ${SREADER_DATASOURCE_PASSWORD:sreader}
  flyway:
    enabled: true
    locations: classpath:db/migration
  jooq:
    sql-dialect: postgres

sreader:
  scheduler:
    enabled: ${SREADER_SCHEDULER_ENABLED:true}
    cron: ${SREADER_SCHEDULER_CRON:0 */15 * * * *}
  job:
    run-once: ${SREADER_JOB_RUN_ONCE:false}
  http:
    user-agent: ${SREADER_HTTP_USER_AGENT:SReader/0.1}
    connect-timeout: 5s
    read-timeout: 20s
```

実際の property 名は Spring Boot の Binder で扱いやすい形にしてください。

## テスト方針

新アプリの test は Spring Boot の標準作法に寄せてください。

要件:

* JUnit Jupiter を使う。
* Spring Boot Test を使う。
* DB を使う integration test は Testcontainers PostgreSQL を優先する。
* scheduler は test では default 無効にする。
* repository test は jOOQ + PostgreSQL で検証する。
* service test は外部 HTTP を mock server で検証する。
* feed parse test は fixture XML を使う。
* 本文抽出 test は fixture HTML を使う。
* Gmail / SMTP / login rule / auth fetch の test は作らない。
* legacy JUnit 4 / DBUnit / EasyMock は新アプリに持ち込まない。

最低限ほしい test:

1. Flyway migration が PostgreSQL に適用できる。
2. jOOQ generated code が compile される。
3. feed URL を登録できる。
4. duplicate feed URL を抑止できる。
5. feed XML から content header を保存できる。
6. duplicate article URL を抑止できる。
7. HTML fixture から full text を抽出・保存できる。
8. scheduler disabled 時に定期実行されない。
9. run-once property で1回実行できる。

## 旧コードの扱い

既存の `commons` / `batch` / Hibernate / DAO / shell script をどう扱うか、調査して判断してください。

推奨:

* 新 Spring Boot app に移植が完了した legacy code は削除する。
* 一度に削除すると危険な場合は、標準 build から外し、legacy-only と明記する。
* Hibernate mapping / DAO / entity は、新アプリで使わないなら削除する。
* `batch/script/run_feedreader.sh` は Spring Scheduler へ置換するため、標準手順から削除する。
* README から cron 実行説明を削除する。
* legacy script を残す場合は、ファイル先頭または docs に legacy-only と明記する。

最終的に、標準操作は Spring Boot app container に統一してください。

## README / docs 更新

`README.md` と `docs/modernization.md` を更新してください。

必ず記録してください。

* Java 25 + Spring Boot + jOOQ + Flyway + PostgreSQL 16 にリライトしたこと
* Hibernate / legacy DAO を標準構成から外したこと
* `batch/script/run_feedreader.sh` / cron 方式を廃止し、Spring Scheduler に置き換えたこと
* Docker Compose の標準起動手順
* PostgreSQL 起動手順
* Flyway migration 手順
* jOOQ code generation 手順
* app container 起動手順
* scheduler 設定方法
* scheduler を無効化する方法
* run-once 実行方法
* test 実行方法
* Gmail 配信と認証付き取得は廃止済みで復活していないこと
* ホストに Java / Maven / PostgreSQL client / Flyway を入れないこと
* 既存 PostgreSQL volume を破棄する場合の注意
* 残課題

## 検証コマンド

最低限、以下を Docker Compose 経由で実行してください。

```sh id="yn6uw4"
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
```

PostgreSQL 確認:

```sh id="klyigx"
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
```

Flyway migration:

```sh id="chz29d"
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
```

jOOQ code generation と build:

```sh id="9x12io"
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean generate-sources
docker compose run --rm maven mvn clean verify
```

Spring Boot app build:

```sh id="xg4y4e"
docker compose build app
```

app 起動:

```sh id="391s29"
docker compose up app
```

手動 run-once:

```sh id="fx24qk"
docker compose run --rm app java -jar /app/app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

scheduler 無効化起動:

```sh id="nhp0na"
docker compose run --rm -e SREADER_SCHEDULER_ENABLED=false app
```

schema 確認:

```sh id="p31sw2"
docker compose exec postgres psql -U sreader -d sreader -c "\dt"
docker compose exec postgres psql -U sreader -d sreader -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

legacy 依存チェック:

```sh id="en283f"
git grep -n -E "Hibernate|hibernate|SessionFactory|Session|Criteria|hbm.xml|javax.persistence|jakarta.persistence|spring-boot-starter-data-jpa|run_feedreader|cron|Gmail|SMTP|login_rules|auth_password|jdbc:mysql|mysql-connector" || true
```

この検索で legacy-only docs がヒットする場合は許容しますが、標準 runtime / build / test path に残っている場合は修正してください。

## 期待する完了状態

作業完了時点で、以下を満たしてください。

* Java 25 の Spring Boot app が標準成果物になっている。
* PostgreSQL 16.x を使う。
* Flyway migration が使われている。
* jOOQ で DB access している。
* Hibernate / JPA は標準 runtime から消えている。
* 旧 DAO は標準 runtime から消えている。
* `batch/script/run_feedreader.sh` / cron 方式は標準手順から消えている。
* Spring Scheduler がアプリケーションコンテナ内で feed reader job を定期実行する。
* scheduler は property で有効/無効を切り替えられる。
* run-once 実行手段がある。
* Docker Compose だけで build/test/run できる。
* README / docs が新構成に一致している。
* Gmail 配信機能と認証付き取得機能が復活していない。
* `mvn clean verify` が成功する。失敗する場合は、原因と残課題が明確に記録されている。

## 今回やらないこと

以下は今回のスコープ外です。

* Web UI の本格実装
* ユーザー認証
* Gmail 配信の復活
* SMTP 配信の復活
* 認証付き取得の復活
* MySQL 互換
* 本番データ移行
* Kubernetes 化
* 分散 scheduler / cluster lock
* Quartz 導入
* 高度な crawler 化
* full-text search engine 導入
* 本番監視設計
* secret manager 導入

必要に見えても、今回の作業では実装せず、docs の残課題に記録するだけにしてください。

## 成果物

最終的に、以下のような変更を含む patch を作ってください。

* Spring Boot app module または新しい単一 app 構成
* Java 25 対応 Maven 設定
* Spring Boot dependency 設定
* jOOQ dependency / codegen 設定
* Flyway migration 整理
* PostgreSQL 16.x 対応 Docker Compose
* app Dockerfile
* Spring Scheduler 実装
* run-once 実行手段
* RSS/Atom 取得 service
* 本文抽出 service
* jOOQ repository
* application.yml
* tests
* README 更新
* docs 更新
* legacy code の削除または legacy-only 明示

## 最終報告フォーマット

作業完了後、以下の形式で報告してください。

1. 変更概要
2. 新しい module / package 構成
3. 採用した Spring Boot / Java / jOOQ / Flyway / PostgreSQL version
4. 旧 Hibernate / DAO / batch script の扱い
5. Flyway schema 方針
6. jOOQ code generation 方針
7. Spring Scheduler の実装内容
8. run-once 実行方法
9. Docker Compose の変更内容
10. README / docs の更新内容
11. 実行した Docker / Maven / Flyway / PostgreSQL コマンド
12. 成功した検証
13. 失敗した検証があれば、その原因
14. 判断に迷った点と採用した判断
15. 残課題
16. 次の推奨ステップ

この作業では、既存の古い実装を延命するのではなく、SReader の中核ドメインである「認証不要な公開 RSS/Atom feed の取得、記事 URL/タイトル保存、本文抽出、DB 保存」を、Spring Boot の典型的なアプリケーションとして作り直すことを最優先してください。
