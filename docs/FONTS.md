# IMEのフォント

設定値はSystem（端末の標準フォント）、Noto Sans JP、Bizin Gothic Discordの3種類です。追加フォントを同梱するため、実行時のネット接続は不要です。

| フォント | 配布元 | 同梱ファイル |
| --- | --- | --- |
| Noto Sans JP | [Google Fonts](https://github.com/google/fonts/tree/main/ofl/notosansjp) | `NotoSansJP[wght].ttf` を `NotoSansJP.ttf` として保存。内部バージョン2.004-H2、wght=400を明示指定 |
| Bizin Gothic Discord | [公式リリースv0.0.4](https://github.com/yuru7/bizin-gothic/releases/tag/v0.0.4) | `BizinGothicDiscord_v0.0.4.zip` の `BizinGothicDiscord-Regular.ttf` |

フォントのバイナリは変更していません。各OFLとフォント内の著作権・ライセンス表記を `prototype/src/main/assets/licenses/` に保存し、アプリの設定「ライセンス」から表示します。

変換候補は、同じ文字サイズならフォントを切り替えても同じ行高になります。日本語の「国」の字形を基準に描画位置を調整し、Noto Sans JPやBizin Gothic Discordのフォント全体の上下余白で候補欄が広がったり、文字がずれたりする差を抑えます。全角・半角・AI注記は標準フォントで下部の専用領域へ表示します。

文字幅の分類には [Unicode 16.0 EastAsianWidth.txt](https://www.unicode.org/Public/16.0.0/ucd/EastAsianWidth.txt) を同梱しています。注記は全角・半角の候補が重なる場合や、見分けにくい単独の英数字・記号などに絞り、「幅可変」は表示しません。「二／ニ」「口／ロ」「へ／ヘ」などには漢字・カナ・かなの注記を付けます。注記とAI表示は設定「表示とフォント」で個別に切り替えられます。候補文字は注記の有無にかかわらず区切り線の中央に配置します。
