# 正式版のビルドと配布

- アプリ名: Glide 日本語入力
- バージョン: 1.1.10（versionCode 29）
- アプリID: `net.neomenia.japaneseinput.glide`
- 配布APK: `dist/Glide-1.1.10.apk`
- Android 8.0以降、arm64-v8a／x86_64、ネットワーク権限なし

1.1.10はLINEで「改行で送信」がOFFでもEnterで送信される不具合を修正します。アプリが `IME_FLAG_NO_ENTER_ACTION` を指定した場合は、独自の送信アクションがあっても改行を優先します。改行・送信の切り替えを含む回帰テスト7件を追加しました。ユーザーによるテスト版でのLINE実機確認は完了しています。

ユニットテスト105件成功、releaseビルド・lint成功（prototype: 0 errors／24 warnings）。正式版ID・バージョン、既存APKと同じ署名、debuggableフラグなし、16KB ZIP alignmentを検証済みです。

- 1.1.10 APK SHA-256: `2099df04a51994a6cc00b16c9e71c68f354c4cb1a6a85eb6d11d8731f3139ab6`

1.1.9は入力中のアプリを見たまま調整するIME内のサイズ設定、設定メニューの選択、左右境界バーのドラッグ調整、青系のMaterial 3設定画面、候補行末の区切り線削除を含みます。ユニットテスト98件（入力・辞書92件、Android画面・描画6件）成功、releaseビルド・lint成功（prototype: 0 errors／24 warnings）。ネイティブ描画テストで幅のドラッグ・保存、メニュー選択、文字のコントラスト、候補の内部区切り線を保ちつつ行末の線がないことを確認しました。ID・バージョンと既存と同じ署名を検証済みです。実機でのIME操作は未確認です。

- 1.1.9 APK SHA-256: `1fa6656705f0d41f8d9f0d7bfd4d97d96baa1fa5a706aacfa0528e1bb91631e3`

1.1.8は設定専用アプリへの移行を含みます。「入力」「変換」「表示」「このアプリについて」の4項目とし、記号入力ボードは入力、大きさ・位置は表示、ライセンスはこのアプリについてへ移しました。設定画面だけにMaterial Components 1.12.0を使用し、IMEの配列・キー描画と保存済みの値を維持します。サイドバー幅は専用の色分けプレビューで調整し、設定アイコンは6枚歯です。

ユニットテスト96件（入力・辞書92件、設定画面4件）成功、releaseビルド・lint成功（prototype: 0 errors／23 warnings）。RobolectricのAndroid 9ネイティブ描画で設定画面とサイズ調整の起動・項目構成・保存・ダイアログを確認し、`build/reports/settings-ui/` の画像を確認しました。APKのID・バージョン・既存と同じ署名、練習用ActivityがDEXから削除されたこと、起動先が設定画面であることを検証済みです。実端末での表示・操作は未確認です。

- 1.1.8 APK SHA-256: `a2c2f650b6d460b9282a6ec702f85c01fc0306189c86eb8053af223536f84d9a`

1.1.7は紛らわしい文字だけの注記、漢字・かなの区別、注記とAI表示の個別ON/OFF、行全体の中央揃え、設定の項目別表示、左右サイドバー幅、中央寄せ、ピンチ拡縮を含みます。ユニットテスト92件成功、releaseビルド・lint成功（prototype: 0 errors／28 warnings）。APKの正式版ID・バージョン・従来と同じ署名を検証しました。文字注記の判定とピンチのサイズ制限はホストで検証済みです。Androidの配置・注記切り替え・保存と取消・中央寄せ・2本指操作・サイドバーのタッチ判定チェックはコンパイル済みですが、接続端末がなく実行・実機表示確認は未実施です。

- 1.1.7 APK SHA-256: `093dc1d80c5df12b302563aaa04f9d638dca70fc877c4075fcb98b3066b9f032`

1.1.6は候補の文字サイズに応じた共通行高と、日本語の字形を基準にした縦中央揃えを含みます。Noto Sans JP・Bizin Gothic Discordの余白差を抑え、全角・半角・AI注記は標準フォントで表示します。ユニットテスト89件成功、releaseビルド・lint成功（prototype: 0 errors／25 warnings）。正式版ID・バージョン・従来と同じ署名を確認しました。3フォント×3文字サイズの通常・展開表示の配置・描画チェックを追加しましたが、接続端末がないため実行・実機表示確認は未実施です。

- 1.1.6 APK SHA-256: `69b76c8e5834b71cc885651913711b6cd148d865f4a888dc6b377e19763875b3`

1.1.5はEU・IU無効時のKEU・KIU→KU、候補文字サイズに応じた欄の高さ、各行の候補への均等な余白配分を含みます。ユニットテスト89件成功、releaseビルド・lint成功（prototype: 0 errors／25 warnings）。APKの正式版ID・バージョン・署名を確認しました。実機での表示確認は未実施です。

- 1.1.5 APK SHA-256: `2c20534d2328323fcde8b054e5b1c186f79742aa085b6c42ecdd96ab38c50ec7`
- 署名証明書は従来の正式版と同じです。

インストール後、設定アプリ内の有効化・切り替えボタンでAndroidのIMEとして選択します。IMEの登録は [Android公式のInputMethodServiceの手順](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) に従っています。アプリは設定専用です。`SettingsActivity` は変換エンジンを起動せず、入力・候補・記号一覧はIMEの `KeyboardSession` が担当します。大きさ・位置は設定専用のプレビューを使い、既存の設定ファイルを引き継ぎます。

## ビルド

JDK 17、Android SDK 35、NDK 28.2.13676358、CMake 3.22.1、Gradle 8.11.1を用意します。zenzソースの準備方法は `zenz/README.md` を参照してください。

```powershell
./scripts/build-release.ps1 -JavaHome 'JDKのパス' -AndroidSdk 'SDKのパス' -GradleExecutable 'gradle.batのパス'
```

SDKとNDKの保存先が別の場合は `-NativeSdk`、既存の依存キャッシュを利用する場合は `-ReadOnlyCache` を指定できます。準備済みなら `-Offline` を指定できます。スクリプトはユニットテスト・releaseビルド・lintを実行し、APKのIDと署名を検証してSHA-256ファイルを出力します。正式版は専用のRSA 3072ビット鍵で署名し、debuggableフラグを付けません。テスト版は別ID `.debug` とデバッグ鍵を使用します。

## 署名鍵の保管

初回のみ `.tools/signing/glide-release.p12` と `password.clixml` を生成します。このフォルダはWindowsの現在のアカウントとSYSTEMだけがアクセスでき、`.tools/` はソース管理対象外です。パスワードはランダム生成し、Windows DPAPIで暗号化して保存します。ビルド時だけ環境変数でkeytool／Gradleに渡し、ログ・APK・distには含めません。

更新版には同じ鍵が必要です。署名鍵は安全な場所へバックアップし、パスワードは別途パスワード管理ソフトなどで保管してください。`password.clixml` は同じWindowsアカウント・マシンでの復号を前提としており、このファイルを別PCにコピーするだけでは復旧できません。署名鍵かパスワードの片方が欠けた場合、ビルドは新しい鍵を自動生成せず停止します。

## 検証範囲

ホストのユニットテスト、release lint、APKのID・バージョン・署名・IMEサービス登録を検証します。IMEの実端末検証は別途必要です。端末確認では、有効化・切り替え、他アプリの日本語／英字／絵文字、変換中の欄移動・キーボード非表示・画面回転、検索・送信・複数行のエンター、選択範囲と絵文字の削除、パスワード欄、全設定の反映を確認します。

2026-10-21の1.1.4作成結果: ユニットテスト88件成功、release／debug lintはいずれも0 errors／24 warnings、APKの署名・ID・バージョン・IME登録と16KB ZIP alignmentの検証に成功しました。arm64-v8a／x86_64のJNIと固定版zenzモデルを同梱しています。Android回帰チェック用APKはコンパイル済みですが、接続端末がないため実行・タッチ操作の確認は行っていません。

- APK SHA-256: `2e39604cc0fe9692a3efad148c197acba4276fa419f616f49a6be060b21e195b`
- 署名証明書 SHA-256: `d83837499efec47a845991409fd9dc426d94bb0eff0f5eb4258c077927c50349`

Androidの実際のInputConnection・変換中範囲を使う回帰チェックは `prototype/src/androidTest/` にあります。Gradleの `:prototype:assembleDebugAndroidTest` で作成したテストAPKとdebug APKを端末へインストールして、次で実行します。

```powershell
adb shell am instrument -w net.neomenia.japaneseinput.glide.debug.test/net.neomenia.glideprototype.SessionInstrumentation
```

成功時は `InputConnection regression checks passed` と `INSTRUMENTATION_CODE: -1` が出ます。このチェックは入力セッションの回帰用であり、システムのIME選択・実際のタッチ操作の確認は上記の端末検証で行います。

1.1.4はNNのタップ入力、長文の候補探索とAI出力検証、変換候補文字サイズ12〜30spの設定を含みます。長文の調査結果は [変換の検証](CONVERSION_QUALITY.md) を参照してください。候補サイズの通常／展開表示・文字高確保のAndroidチェックもコンパイル済みですが、実機での表示確認は未実施です。
