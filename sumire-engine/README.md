# Sumire由来の基礎変換処理

操作試作のかな漢字変換に、KazumaProjectの `kotlin-kana-kanji-converter` の基礎変換コードと、Android版Sumire (`JapaneseKeyboard`) の基本辞書を組み合わせて使う。

- Android版Sumire: `fb8319890089be32f284e5e7fc2b438f783b6d28`
- 基礎変換コード: `d5bcefcf613b238966242a2aac3771ef9a18e12d`
- 各辞書のSHA-256と採用ファイル: `UPSTREAM.json`

基礎変換のLOUDS検索・トークン辞書・接続コスト・グラフ構築・経路探索を流用している。Android版Sumireの `KanaKanjiEngine` 全体、文脈予測、学習、ユーザー辞書、N-gram資産、拡張辞書は移植していない。そのためAndroid版Sumireと同じ候補順や変換結果を保証しない。

0.1.15から別モジュール `zenz` でAI候補を追加する。基本辞書の変換経路は維持し、モデルと検証範囲は `zenz/README.md` を参照。

元コードには辞書フォルダ・品詞表パスの外部指定とキャンセル判定を追加している。1.1.4では、負の単語・接続コストで誤順位や探索の爆発を起こす前向き探索を、Viterbiの前方コストを使う後向きA*探索へ修正した。辞書にないコードポイントはリテラル経路で保持し、AI候補が読み全体に対応するかを検証する処理も追加した。候補順は上流から変更される。Android側の `SumireAdapter` が同梱ZIPをアプリ内へ展開して初期化し、128件の読みをメモリにキャッシュする。辞書の準備と変換はUIとは別のスレッドで行う。

MITの著作権・ライセンス文、辞書に付属するMozc・IPADIC等の通知を `LICENSE` と `prototype/src/main/assets/licenses/` に保持している。辞書は一律にMITではない。アプリ内「設定→ライセンス」からも通知を確認できる。

実際に同梱する辞書を使ったホストテストで「にほん→日本」「がっこう→学校」「きょう→今日」を確認する。Android実機での動作・性能と、Android版Sumireの全機能との一致は別の検証事項とする。

参照:

- https://github.com/KazumaProject/JapaneseKeyboard
- https://github.com/KazumaProject/kotlin-kana-kanji-converter

1.1.4の長文処理は共有の`ConversionPlan`でかなとリテラルを分け、かなを最大128コードポイントずつ変換する。辞書の第一候補を先頭に保ち、AI候補を追加する。ホストテストは負のコスト・ランダムな小規模格子の全経路比較、長文、URL・絵文字の保持、AIの脱落・付加検出も含む。
