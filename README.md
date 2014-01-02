SReaderとは
==========

SReaderは、RSS/Atomリーダーです。

携帯電話などの端末で、回線状況のよくない状況で、快適に記事を読むことができることを目標に、設計されています。

**サーバーで動作**。SReaderは、携帯端末よりパワフルで、回線状況が安定していて、定期的に記事を収集できる、オフィスや自宅に据え置くパソコンで動作します。

**全文取得**。ウェブページには、回線状況がよくない時にはウザいだけの装飾で溢れています。SReaderは、ウェブページから記事本文だけを切り出して配信します。

**認証対応**。携帯端末において、パスワード入力はもっとも煩わしい作業のひとつです。IDとパスワード、ログイン方法を設定すれば、ログインの必要なウェブサイトの記事も自動収集できます。

**Gmailで配信**。携帯端末でもっとも軽快に動作し、利用者がもっとも使い慣れたアプリは、電子メールクライアントです。そのため、新たに携帯端末向けのアプリを作るより、電子メールで配信するのがベストだと考えました。

入手方法 & セットアップ手順
-----------------------

JDK7、MySQL、Git、Maven3.xをあらかじめPATHの通った場所にインストールしてください。

	sudo apt-get install openjdk-7-jdk mysql-server git-core maven
	git clone git://github.com/sasasin/sreader.git

DBを構築します。Gmail配信を使用するため、アカウント情報をデータベースに登録します。gmail.sqlは適宜修正して使用してください。

	mysql -u root -p
	source ./sreader/commons/script/ddl.mysql.users.sql

	use sreader;
	source ./sreader/commons/script/ddl.mysql.tables.sql
	source ./sreader/commons/script/dml.sql
	source ./sreader/commons/script/gmail.sql
	commit;

	use sreadertest;
	source ./sreader/commons/script/ddl.mysql.tables.sql
	source ./sreader/commons/script/dml.sql
	source ./sreader/commons/script/gmail.sql
	commit;

sreaderをセットアップします。

	./sreader/build_all.sh

$HOME/sreader.txtを作成し、収集対象のRSS/AtomのURLを、一行にひとつずつ記載してください。

	http://example.com/rss.xml\n
	http://hoge.co.jp/atom.xml\n
	http://foo.baa.net/?feed\n
	
cronなどに、下記スクリプトを任意の間隔で実行するよう設定してください。

	./sreader/batch/run_feedreader.sh


認証情報の設定方法
---------------

下記形式で、sreader.txtに記載してください。

	http://foo.baa.net/?feed\tアカウントID\tパスワード\n

SReaderは、この情報と、login_rulesテーブルの設定で、ログインを試みます。dml.sqlを参考に、ドメイン名、formがPOSTを飛ばすURL、IDを入力するテキストボックスのname属性、パスワードを入力するテキストボックスのname属性を設定してください。


配布条件
------

本プログラムはフリーソフトウェアです。LGPL (the GNU General Public License)バージョン3、またはそれ以降のバージョンに示す条件で本プログラムを再配布できます。LGPLについてはLICENSEファイルを参照して下さい。
