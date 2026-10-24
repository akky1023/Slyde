# IMEのフォント

設定値はSystem（端末の標準フォント）、Noto Sans JP、Bizin Gothic Discordの3種類です。追加フォントを同梱するため、実行時のネット接続は不要です。

| フォント | 配布元 | 同梱ファイル |
| --- | --- | --- |
| Noto Sans JP | [Google Fonts](https://github.com/google/fonts/tree/main/ofl/notosansjp) | `NotoSansJP[wght].ttf` を `NotoSansJP.ttf` として保存。内部バージョン2.004-H2、wght=400を明示指定 |
| Bizin Gothic Discord | [公式リリースv0.0.4](https://github.com/yuru7/bizin-gothic/releases/tag/v0.0.4) | `BizinGothicDiscord_v0.0.4.zip` の `BizinGothicDiscord-Regular.ttf` |
| Noto Sans Symbols | [Noto公式ビルド](https://notofonts.github.io/symbols/) | `NotoSansSymbols-Regular.ttf`、内部バージョン2.003 |
| Noto Sans Symbols2 | [Noto公式ビルド](https://notofonts.github.io/symbols/) | `NotoSansSymbols2-Regular.ttf`、内部バージョン2.008 |

フォントのバイナリは変更していません。各OFLとフォント内の著作権・ライセンス表記を `prototype/src/main/assets/licenses/` に保存し、アプリの設定「ライセンス」から表示します。

記号ボード・変換候補・キーの文字は、選択フォント → Noto Sans JP → Noto Sans Symbols → Noto Sans Symbols2 → 端末の標準フォントの順で補完します。同じフォントの重複は実質的にスキップします。System選択時は標準フォントで表示できる文字を優先します。

記号・絵文字の変換候補はVS15・VS16を除いた素の文字列を入力します。肌色・髪色の派生は候補から除外します。国旗・家族・職業などの結合列は維持し、見た目はフォントの既定の表示に従います。詳細は[記号辞書](SYMBOLS.md)を参照してください。

Android 8以降で同じ順序にするため、同梱フォントの実際の収録文字情報を照合して、文字のまとまりごとに描画フォントを指定します。単純な`Paint.hasGlyph`だけでは端末の代替フォントまで含めて判定するため、同梱フォントの優先順を保てません。結合した絵文字や絵文字表示指定は端末の絵文字フォントにまとめて渡し、国旗・肌色・家族などの列を分割しません。

Symbols系でも未収録で、端末の標準フォントにもない文字は豆腐表示が残ります。文字そのものや入力内容は変更しません。収録情報の`fonts/coverage.bin`には元フォントのSHA-256を保存しています。フォント更新時は`python scripts/generate-font-coverage.py`（fonttoolsが必要）で再生成し、テストで元ファイルとの一致を確認します。

Symbols系の同梱ファイルのSHA-256：

- Noto Sans Symbols: `3f98aca41f54a721c9c06d4dfa47d6d84b4a49e116211a29bafcae8f7e292b8a`
- Noto Sans Symbols2: `207bd630c4bdac399099afa80986ef5dfbee3c35a47cab04c4a7ee94fcbd5bec`

変換候補は、同じ文字サイズならフォントを切り替えても同じ行高になります。日本語の「国」の字形を基準に描画位置を調整し、Noto Sans JPやBizin Gothic Discordのフォント全体の上下余白で候補欄が広がったり、文字がずれたりする差を抑えます。全角・半角・AI注記は標準フォントで下部の専用領域へ表示します。

文字幅の分類には [Unicode 16.0 EastAsianWidth.txt](https://www.unicode.org/Public/16.0.0/ucd/EastAsianWidth.txt) を同梱しています。注記は全角・半角の候補が重なる場合や、見分けにくい単独の英数字・記号などに絞り、「幅可変」は表示しません。「二／ニ」「口／ロ」「へ／ヘ」などには漢字・カナ・かなの注記を付けます。注記とAI表示は設定「表示とフォント」で個別に切り替えられます。候補文字は注記の有無にかかわらず区切り線の中央に配置します。
