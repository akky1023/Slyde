# 入力記録ツール

[READMEに戻る](../README.md)

アルテを使って入力した結果と編集履歴を記録するAndroidアプリと、USB経由で画面録画・タッチ軌跡を保存するPCツールです。新しいIMEの入力規則と回帰テスト用の資料を集めます。

## 使い方

1. Galaxy S25でUSBデバッグを有効にし、PC接続を端末で承認します。アルテは普段の設定・サイズ・配列のまま使います。
2. Python 3.10以降とADBを使えるPCで、プロジェクトフォルダから以下を実行します。初回の `-Install` は同梱の記録アプリをインストールします。

```powershell
.\scripts\record.ps1 -Install
```

Windowsでは `start-recording.cmd` をダブルクリックしても開始できます。このPCではCodexに同梱されたPythonも自動検出します。

3. 端末の「アルテ入力記録」で「IME選択」からアルテを選びます。「例題」で入力対象を選んで「記録開始」を押します。
4. 指示された文字を入力します。入力方法は普段どおりで構いません。「次の例題」で現在の結果を保存し、入力欄を空にして次へ進みます。
5. 英字の例題でもアルテ配列を使ってください。「自由入力」では任意の目標文字を設定できます。日本語の変換を試す場合は、自由入力の目標に漢字を指定してください。
6. 終了時はアプリで「記録停止」、PCで **Ctrl+C** を押します。録画の終了とファイル回収が済むまでUSBを抜かないでください。

複数端末がある場合は `-Serial 端末のシリアル` を指定します。`-Python` と `-Adb` で実行ファイルのパスを指定できます。ADBの接続確認は `adb devices -l` です。

記録済みのアプリログだけを後から再回収する場合：

```powershell
.\scripts\record.ps1 -CollectOnly
```

アプリ単体でも記録できます。「書き出し」で全セッションをJSONLとして保存できます。未確定のままの入力も記録するため、例題の最後に必ず確定する必要はありません。

## 保存内容

`captures/日時/` に保存します。

| ファイル | 内容 |
| --- | --- |
| `screen-001.mp4` 以降 | 画面全体の録画。ポインタの軌跡表示付き。約170秒ごとに分割 |
| `screen-start.png` / `screen-end.png` | 録画前後のスクリーンショット |
| `metadata.json` | 解像度・密度・入力デバイスの座標範囲・時計対応・録画区間・取得エラー |
| `display.txt` | 端末の表示設定・回転・表示モードなど |
| `input-devices.txt` | 入力デバイスと座標範囲 |
| `getevent.txt` | タッチイベントの原文。取得失敗の理由も確認可能 |
| `touches.jsonl` | 解析したマルチタッチの座標・指ID・指を離したタイミング |
| `sessions/*.jsonl` | 入力文字・選択範囲・未確定範囲・IME編集命令・例題と実際の結果 |

画面録画は固定サイズへの縮小を指定せず、端末の `screenrecord` の既定サイズを使います。録画機能の制約により実際の動画サイズが画面サイズと異なる場合があるため、動画の実寸と `metadata.json` を併用してください。動画の分割の間には短い欠落があり、その間の映像は復元できません。長い操作は分割境界を避けて再記録してください。

生のタッチ座標は画面のピクセルとは限りません。`rawX/rawY` を保持し、入力デバイスの最小・最大値に基づく `normalizedX/normalizedY`（パネル全体に対する0〜1）も保存します。これらは**キーボード内の正規化座標ではありません**。端末の回転やキーボードの高さ・左右寄せを映像で確認してから、キーボードの矩形に合わせてテスト座標へ変換します。アプリは縦画面を指定します。

アプリの記録中表示には `t=...ms`（端末のuptime）を100msごとに表示します。これを動画内で読めば、入力ログの `uptimeMs` と照合できます。IME呼び出しの回数と指の操作回数は同じとは限りません。動画とタッチのDOWN/UPに相当する追跡IDの開始・終了を併せて判断します。動画とタッチのフレーム単位の自動同期は未実装です。PC側時刻はプロセス開始・終了の範囲であり、厳密な動画PTSではありません。入力位置のインデックスはUTF-16です。

例題の `matchesExpected` は表示された文字列の一致だけを判定します。操作が正しかったことや、変換が確定済みであることは保証しません。全セッションを回収するので、今回の分は `sessionStart` の日時とセッションIDで識別できます。

## 端末による制限と復旧

起動に失敗した場合は、画面に出る原因と `captures/launcher-日時.log` を確認してください。端末が見つからない場合はデータ通信可能なUSBケーブル・USBデバッグ設定を確認し、未承認の場合は端末のロックを解除してPCからの接続を許可します。エラーを報告する際はこのログを添えると、元のADB・Pythonのエラーを確認できます。

通常のアプリは、別のIME上のタッチ軌跡を直接受け取れません。このツールはADBの `getevent` を試し、許可されない端末では数値の軌跡を取得できません。その場合も、ポインタ表示を付けた画面録画と編集履歴を残します。root取得は行いません。Galaxy S25で実際に許可されるかは端末接続後の確認が必要です。

USB記録中だけAndroidの「ポインタの位置」「タップを表示」を有効化し、正常終了・Ctrl+C時は元の値へ戻します。アプリ自身は通信権限を持ちません。PCの記録中は端末の画面とタッチ全体が記録対象になるため、テスト画面で操作してください。

PCの強制終了やUSB切断で復元できなかった場合、Androidの開発者向けオプションで表示を戻せます。録画の回収に失敗した場合は `metadata.json` の各区間の `remote` に端末側のファイルパスを残します。再接続して `adb pull そのパス 保存先` で回収できます。回収成功前の録画を端末から自動削除しません。

## ビルドと確認

JDK 17、Android SDK Platform 35、Build Tools 35.0.0を用意します。Gradle Wrapperは8.11.1、Android Gradle Pluginは8.9.2です。

```powershell
.\scripts\build-recorder.ps1 -JavaHome 'JDK17のパス' -AndroidSdk 'SDKのパス'
python -m unittest discover -s tools -p 'test_*.py' -v
```

APKは `dist/arte-input-recorder-debug.apk` です。アプリIDは `net.neomenia.inputrecorder.debug`。ADBでアプリ内部の記録を回収するため、デバッグAPKを使用します。

公式参照：

- [アルテローマ字入力の使い方](https://www.keyboard-arte.com/arte-on-mozc)
- [開発者ブログ：日本語とスワイプ入力](https://uminekokobo.blogspot.com/2017/11/blog-post.html)
- [Android InputConnectionWrapper](https://developer.android.com/reference/android/view/inputmethod/InputConnectionWrapper)
- [AOSP getevent](https://source.android.com/docs/core/interaction/input/getevent)
