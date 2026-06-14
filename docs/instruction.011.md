# sreader: feed_url の TOML import/export 機能追加

`sasasin/sreader` に、`feed_url` テーブルを TOML 形式でエクスポート・インポートする機能を追加してください。

## 重要な前提

最初に必ず `AGENTS.md` を読み、その内容に従ってください。

特に以下を守ってください。

* 現行の標準構成は `app` module の Spring Boot + jOOQ + Flyway + PostgreSQL である。
* 旧 `batch` / `commons`、Hibernate、MySQL、Gmail/SMTP、認証付き取得、`login_rules` は復活させない。
* Java / Maven / PostgreSQL / Flyway をホストに直接インストールしない。
* 検証は Docker Compose 経由で行う。
* 最終報告は `AGENTS.md` の形式に従う。

## 目的

`feed_url` テーブルを単なる RSS URL の集合ではなく、購読状態を持つ購読レコードとして扱えるようにする。

そのうえで、以下を実現してください。

* `feed_url` を TOML 形式で export できる。
* TOML 形式のファイルから `feed_url` を import できる。
* 購読停止状態も TOML に含められる。
* 過去に購読停止した feed が、古い TOML の import などで誤って復活しにくい。
* 利用者が「もう関心がないので購読停止する」「サイト閉鎖したので購読停止する」といった意図を記録・移行できる。

## DB 設計

Flyway migration を追加し、`feed_url` に以下の列を追加してください。

```sql
status text not null default 'active',
unsubscribe_reason text null,
unsubscribed_at timestamptz null,
note text null
```

制約:

* `status` は `'active'` または `'unsubscribed'` のみ許可する。
* `unsubscribe_reason` は `null` または以下のいずれかのみ許可する。

  * `'not_interested'`
  * `'site_closed'`
  * `'feed_dead'`
  * `'moved'`
  * `'other'`

既存データは migration 後にすべて `status = 'active'` として扱ってください。

購読停止は `feed_url` の DELETE ではなく、`status = 'unsubscribed'` への更新で表現してください。
既存の `content_header` / `content_full_text` は削除しないでください。

## TOML スキーマ

TOML の形式は以下とします。

```toml
schema_version = 1
generated_at = "2026-06-14T12:00:00+09:00"

[[feeds]]
url = "https://example.com/feed.xml"
status = "active"

[[feeds]]
url = "https://closed.example/rss.xml"
status = "unsubscribed"
unsubscribe_reason = "site_closed"
unsubscribed_at = "2026-06-14T12:00:00+09:00"
note = "サイト閉鎖を確認したため"
```

仕様:

* `schema_version = 1` を必須にする。
* まずは `schema_version = 1` のみ対応する。
* export 時には `generated_at` を出力する。
* `feeds` は array of tables とする。
* 各 `feed` は `url` を必須にする。
* `status` は省略時 `active` とする。
* `status` の指定可能値は `active` / `unsubscribed` のみ。
* `status = "unsubscribed"` の場合、以下を扱えるようにする。

  * `unsubscribe_reason`
  * `unsubscribed_at`
  * `note`
* `status = "unsubscribed"` で `unsubscribe_reason` が省略された場合は `other` として扱う。
* 同一 TOML 内で正規化後 URL が重複した場合は validation error にする。
* URL は trim する。
* 空文字 URL は拒否する。
* `http` / `https` の absolute URI のみ許可する。
* userinfo を含む URL は認証付き取得につながるため拒否する。

  * 例: `https://user:pass@example.com/feed.xml` は拒否する。

## import セマンティクス

URL を同一性のキーとして扱ってください。

TOML に `id` は不要です。
import 時に既存レコードがない場合だけ、現在の実装と整合する方法で `id` を生成してください。既存の `HashIds.md5(normalizedUrl)` 相当のルールがある場合はそれを使ってください。

import の基本動作は safe merge とします。

### 基本ルール

| DB 側           | TOML 側         | import 結果                         |
| -------------- | -------------- | --------------------------------- |
| なし             | `active`       | 新規購読として insert                    |
| なし             | `unsubscribed` | tombstone として insert。ただし取得対象にはしない |
| `active`       | `active`       | no-op                             |
| `active`       | `unsubscribed` | 購読停止に更新                           |
| `unsubscribed` | `unsubscribed` | 必要なメタデータのみ更新                      |
| `unsubscribed` | `active`       | デフォルトでは復活させず conflict として報告       |

### unsubscribed から active への復活

DB 側が `unsubscribed`、TOML 側が `active` の場合、デフォルトでは購読を復活させないでください。

このケースは conflict として import 結果に含めてください。

明示的なオプション、たとえば `--resubscribe` が指定された場合のみ、以下の更新を行ってください。

* `status = 'active'`
* `unsubscribe_reason = null`
* `unsubscribed_at = null`
* 必要であれば `note` も null または active 用の値に更新

### TOML に存在しない DB feed

デフォルトの import では、TOML に存在しない DB 側の feed は触らないでください。

つまり、import のデフォルト動作は「TOML に書かれた feed だけを upsert/update する merge」です。

sync モードを実装する場合は、明示オプションでのみ有効化してください。
sync モードでは、TOML に存在しない DB 側の active feed を `unsubscribed` にできますが、誤操作を避けるため dry-run で変更予定を確認できるようにしてください。

## export セマンティクス

export のデフォルトでは、`active` と `unsubscribed` の両方を出力してください。

理由は、`unsubscribed` は単なる不要データではなく、「もう購読しない」という利用者の意思を表す tombstone だからです。これを export に含めることで、別環境への移行や古い TOML の import 時に、止めた feed が誤って復活しにくくなります。

仕様:

* デフォルトでは `active` と `unsubscribed` の両方を出力する。
* `--active-only` オプションで `active` だけを出力できるようにする。
* 出力順は URL 昇順にして deterministic にする。
* `schema_version = 1` を出力する。
* `generated_at` を出力する。
* `status = "active"` の feed には、原則として `unsubscribe_reason` / `unsubscribed_at` は出力しない。
* `status = "unsubscribed"` の feed には、存在する場合 `unsubscribe_reason` / `unsubscribed_at` / `note` を出力する。

## feed reader job の変更

記事取得対象は `status = 'active'` の `feed_url` のみにしてください。

repository には用途別のメソッドを用意してください。たとえば以下のような分離を検討してください。

```java
findActiveForReading()
findAllForExport(...)
findByUrl(...)
upsertFromImport(...)
unsubscribe(...)
resubscribe(...)
```

既存の `findAll()` を安易に取得ジョブで使い続けると、`unsubscribed` feed まで取得対象になる可能性があるため注意してください。

## 実装方針

`app` module 内に TOML import/export 用の service を追加してください。

実装上の注意:

* URL normalize ロジックは既存の `FeedRegistrationService` と重複させず、共通化を検討する。
* import/export のコアロジックはテストしやすい service として実装する。
* parser/serializer は小さく保つ。
* 外部 TOML dependency を追加する場合は `app/pom.xml` に追加し、禁止依存が混入していないことを確認する。
* import 結果として、少なくとも以下の件数を返すかログ出力できるようにする。

  * inserted
  * updated
  * unchanged
  * unsubscribed
  * resubscribed
  * conflicts
  * errors
* dry-run では DB を変更しない。
* validation error は利用者が修正しやすいメッセージにする。

  * どの feed entry が問題か分かるようにする。
  * 可能であれば URL や index を含める。

## CLI / 実行インターフェース

既存のアプリ構成に合わせて、自然な方法で import/export を実行できるようにしてください。

実装候補:

* Spring Boot の `CommandLineRunner`
* dedicated command/service
* 既存の運用方法に合う npm-like / make-like wrapper がある場合はそれに合わせる

最低限、以下の操作が可能になるようにしてください。

```sh
# export
export feeds to TOML

# export active feeds only
export feeds to TOML with active-only option

# import dry-run
import feeds from TOML with dry-run option

# import
import feeds from TOML

# import and allow explicit resubscribe
import feeds from TOML with resubscribe option
```

実際のコマンド名・引数形式は、既存のプロジェクト構成に合わせて決めてください。
決めた形式は README または docs に明記してください。

## テスト

以下をテストしてください。

### TOML validation

* `schema_version` がない場合は error。
* 未対応の `schema_version` は error。
* `feeds[].url` がない場合は error。
* URL が空文字の場合は error。
* URL が `http` / `https` 以外の場合は error。
* userinfo 付き URL は error。
* `status` が不正な場合は error。
* `unsubscribe_reason` が不正な場合は error。
* 同一 TOML 内で URL が重複した場合は error。

### import

* DB に存在しない active feed を insert できる。
* DB に存在しない unsubscribed feed を tombstone として insert できる。
* DB active + TOML active は no-op。
* DB active + TOML unsubscribed は `status = 'unsubscribed'` に更新される。
* DB unsubscribed + TOML unsubscribed はメタデータ更新のみ行われる。
* DB unsubscribed + TOML active はデフォルトでは conflict になり、復活しない。
* `--resubscribe` 相当の明示オプションがある場合だけ、DB unsubscribed + TOML active が active に戻る。
* dry-run では DB が変更されない。
* TOML に存在しない DB feed は default merge では変更されない。

### export

* deterministic な順序で出力される。
* デフォルト export では active と unsubscribed の両方が出力される。
* active-only export では active のみ出力される。
* active feed には unsubscribe 系フィールドが出力されない。
* unsubscribed feed には該当する unsubscribe 系フィールドが出力される。

### feed reader job

* `status = 'unsubscribed'` の feed は記事取得対象にならない。
* `status = 'active'` の feed は従来どおり取得対象になる。

### DB / jOOQ

* Flyway migration が fresh DB に適用できる。
* jOOQ generated sources が新しい schema に追従する。
* 既存データが migration 後に `status = 'active'` として扱われる。

## ドキュメント

README または docs に以下を追加してください。

* TOML import/export の使い方。
* TOML schema。
* `status` の意味。
* `unsubscribe_reason` の意味。

  * `not_interested`: もう関心がない。
  * `site_closed`: サイト閉鎖。
  * `feed_dead`: feed が取得不能・壊れている。
  * `moved`: 移転済み。
  * `other`: その他。
* default import が safe merge であること。
* TOML に存在しない DB feed はデフォルトでは変更されないこと。
* `unsubscribed` は tombstone として export に含まれること。
* `--active-only` 相当の export option。
* `--resubscribe` 相当の import option。
* dry-run の使い方。
* sync モードを実装した場合は、その危険性と dry-run 推奨。

旧 `batch` / `commons`、MySQL、Hibernate、Gmail/SMTP、認証付き取得、`login_rules` を現行機能として docs に書かないでください。

## 検証

`AGENTS.md` に記載された通常確認コマンドを実行してください。

DB / Flyway / jOOQ を変更するため、少なくとも以下を確認してください。

* fresh DB で Flyway migration が成功する。
* jOOQ code generation が成功する。
* `mvn clean verify` 相当の検証が成功する。
* dependency tree を確認し、禁止された旧技術が標準 runtime / build / test に復活していない。
* 回帰禁止チェックを実行する。

## 最終報告

最終報告では以下を含めてください。

* 変更したファイル一覧。
* 追加した DB migration の内容。
* 追加した TOML schema の概要。
* import/export のコマンドまたは実行方法。
* 購読停止状態の扱い。
* `unsubscribed -> active` の conflict / `--resubscribe` の扱い。
* 実行した検証コマンドと結果。
* `AGENTS.md` の回帰禁止事項に違反していないこと。
