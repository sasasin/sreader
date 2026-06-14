# 依頼内容

`sasasin/sreader` リポジトリに対して、`feed_url` テーブルおよび TOML import/export に、記事本文取得方法を表す設定 `full_text_method` を追加してください。

今回のスコープでは、本文取得処理そのものの分岐実装は行わないでください。  
`full_text_method` がどの値で設定されていても、本文取得は現行実装のままで構いません。  
ただし、将来 `full_text_method` によって本文取得実装を分岐できるよう、DB・ドメインモデル・TOML import/export までを整備してください。

## 追加する本文取得方式

`full_text_method` は次の6値のみを許可してください。

| 値 | 意味 |
|---|---|
| `http` | 現行実装。記事URLを通常HTTP GETし、既存のXPath抽出ルール→body textフォールバックで取得 |
| `feed` | RSS/Atomエントリに含まれる本文を全文とみなす |
| `playwright` | 記事URLをPlaywrightで開いたHTMLを、現行のXPath抽出ルール→body textフォールバックに流す |
| `playwright_readability` | 記事URLをPlaywrightで開いたHTMLからreadability4jで本文取得 |
| `playwright_infy_scroll` | Playwright + Infy Scrollでページネーション追跡したHTMLを、現行のXPath抽出ルール→body textフォールバックに流す |
| `playwright_infy_scroll_readability` | Playwright + Infy Scrollでページネーション追跡したHTMLからreadability4jで本文取得 |

## DB変更

`feed_url` テーブルに以下の列を追加してください。

```sql
full_text_method varchar(64) not null default 'http'
````

加えて、以下6値のみを許可する CHECK constraint を追加してください。

```sql
CHECK (
  full_text_method IN (
    'http',
    'feed',
    'playwright',
    'playwright_readability',
    'playwright_infy_scroll',
    'playwright_infy_scroll_readability'
  )
)
```

既存のFlyway migrationに合わせて、新規migrationファイルを追加してください。

## Javaドメインモデル

`FullTextMethod` enum を追加してください。

要件:

* enum名は Java convention に従う
* 永続化・TOML表現用の文字列値は上記6値
* `value()` で文字列値を返す
* `fromValue(String value)` で文字列値から enum を復元する
* 未知の値は `IllegalArgumentException` にする

例:

```java
HTTP("http"),
FEED("feed"),
PLAYWRIGHT("playwright"),
PLAYWRIGHT_READABILITY("playwright_readability"),
PLAYWRIGHT_INFY_SCROLL("playwright_infy_scroll"),
PLAYWRIGHT_INFY_SCROLL_READABILITY("playwright_infy_scroll_readability")
```

`FeedUrl` record に `fullTextMethod` を追加してください。

既存の簡易コンストラクタでは、デフォルトとして `"http"` を設定してください。

## Repository変更

`FeedUrlRepository` の `feed_url` select/insert/update 処理に `full_text_method` を追加してください。

対象は少なくとも以下です。

* `findActiveForReading()`
* `findAllForExport(boolean activeOnly)`
* `findByUrl(String url)`
* `insertIfAbsent(...)`
* `insertFromImport(...)`
* `unsubscribe(...)`
* `updateUnsubscribedMetadata(...)`
* `resubscribe(...)`

特に TOML import 時に、既存行の `status` が同じでも `full_text_method` が変わっていれば `updated` としてDBに反映されるようにしてください。

必要であれば `updateFullTextMethod(...)` のような専用メソッドを追加してください。

## TOML export変更

TOML export は `schema_version = 2` を出力してください。

各 `[[feeds]]` に `full_text_method` を必ず出力してください。
`http` であっても省略しないでください。

例:

```toml
schema_version = 2
generated_at = "2026-06-14T12:00:00+09:00"

[[feeds]]
url = "https://example.com/feed.xml"
status = "active"
full_text_method = "http"

[[feeds]]
url = "https://example.com/js-heavy/feed.xml"
status = "active"
full_text_method = "playwright_readability"
```

## TOML import変更

import は `schema_version = 1` と `schema_version = 2` の両方を受け入れてください。

* `schema_version = 1`

  * 既存形式として扱う
  * `full_text_method` がなければ `"http"`
  * もし `full_text_method` が書かれていても、妥当な値なら受け入れてよい

* `schema_version = 2`

  * `full_text_method` を読む
  * 省略時は `"http"`
  * 6値以外は validation error

* `schema_version` 未指定は従来通り validation error

import時の状態遷移は以下にしてください。

```text
DBなし
  → inserted
  → dry-runでなければ insertFromImport()

DB active / TOML active
  → full_text_method 同一なら unchanged
  → full_text_method 相違なら updated、dry-runでなければDB更新

DB active / TOML unsubscribed
  → updated + unsubscribed
  → dry-runでなければ unsubscribe + full_text_method更新

DB unsubscribed / TOML unsubscribed
  → unsubscribe metadata または full_text_method が変われば updated
  → dry-runでなければ updateUnsubscribedMetadata + full_text_method更新
  → すべて同一なら unchanged

DB unsubscribed / TOML active
  → --resubscribeなしなら conflict
  → --resubscribeありなら updated + resubscribed、status active化 + full_text_method更新
```

`status = "active"` の場合は、現行実装と同様に unsubscribe系メタデータは無視/NULL化してください。
`full_text_method` は `status` に関係なく保持してください。

## 本文取得処理について

今回の変更では、`FullTextExtractionService` などの本文取得処理の分岐は実装しないでください。

ただし、将来的に以下のような分岐が可能になるよう、`FeedUrl.fullTextMethod` が取得できる状態にはしてください。

```java
switch (FullTextMethod.fromValue(feedUrl.fullTextMethod())) {
    case HTTP -> ...
    case FEED -> ...
    case PLAYWRIGHT -> ...
    case PLAYWRIGHT_READABILITY -> ...
    case PLAYWRIGHT_INFY_SCROLL -> ...
    case PLAYWRIGHT_INFY_SCROLL_READABILITY -> ...
}
```

現時点では、どの `full_text_method` が設定されていても、本文取得は現行実装のままで問題ありません。

## テスト

既存テストがあれば更新し、少なくとも以下を確認してください。

* migration後、既存feedは `full_text_method = 'http'` になる
* export結果が `schema_version = 2` になり、各feedに `full_text_method` が出る
* schema_version 1 の既存TOMLを import でき、`full_text_method` は `http` になる
* schema_version 2 のTOMLを import できる
* 不正な `full_text_method` は validation error になる
* DB/TOMLとも active でも `full_text_method` が異なれば updated になる
* dry-run ではDBが変更されない
* unsubscribed/resubscribe の遷移でも `full_text_method` が保持または更新される

## 補足

`full_text_method` 変更後も、既に `content_full_text` に保存済みの記事本文を自動再抽出する必要はありません。
この設定は、現段階では主に今後の本文取得分岐のための feed 単位の設定値として扱ってください。
