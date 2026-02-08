---
title: "Claude Code + iPad + Tailscaleで「どこでもネイティブアプリ開発」環境を作った"
emoji: "🛠"
type: "tech"
topics: ["claudecode", "tailscale", "android", "ios", "remotework"]
published: true
---

## やりたかったこと

iPadを持って外に出て、自宅のMacBookにつないで、手元のAndroidにアプリをデプロイしたい。

https://x.com/rioX432/status/2020501674918547615

普段Android/iOSアプリを開発していて、開発には[Claude Code](https://docs.anthropic.com/en/docs/claude-code/overview)を使っています。ターミナルで動くAIコーディングツールで、コードを書くだけじゃなくビルドやデプロイまでやってくれます。

自宅のMacBookでしか開発できないのがずっと不便だったので、出先でも同じように開発できる環境を作りました。

![構成図](/images/remote-dev-architecture.png)

## iPadからMacを操作する — Jump Desktop

[Jump Desktop](https://jumpdesktop.com/) を使って、iPadから自宅のMacBookをリモート操作しています。

Jump Desktopにはいくつか接続方式がありますが、Fluid接続を使うのがおすすめ。iPadのMagic Keyboardのショートカットがそのまま使えるし、トラックパッドもちゃんと動きます。ほぼMacを直接触っている感覚 👍

XcodeもAndroid Studioも動きます。ただ、IDEの画面はどうしても描画の遅延が気になる。正直なところ、Claude Codeをターミナルで使う方がストレスがない。ターミナルはテキストベースなので軽い。

なので、普段は**Claude Codeにコードを書いてもらって、必要なときだけIDEを確認する**、という使い方に落ち着いています。

## 出先のAndroidにリモートデプロイする — Tailscale + ADB

Jump DesktopでMacは操作できるようになった。でも、ビルドしたアプリをどうやって手元のAndroidに入れるのか。MacとAndroidは物理的に離れているので、USBケーブルはつなげない。

### Tailscaleで解決する

[Tailscale](https://tailscale.com/) は、デバイス同士をVPNでつなぐサービスです。入れるだけで、それぞれのデバイスに `100.x.y.z` という固定のIPアドレスが振られます。

このIPは**WiFiだろうがLTEだろうがテザリングだろうが変わりません**。カフェのWiFiに繋ごうが、スマホのLTEだろうが、同じIPでつながる。これがかなり便利 ✨

### セットアップ

#### 1. Tailscaleを入れる

MacとAndroid、両方にTailscaleを入れて同じアカウントでログイン。これだけでデバイス同士がつながります 🎉

#### 2. ADBをTCPモードにする

一度だけ、Androidの開発者向けオプションでワイヤレスデバッグを有効にして、接続した状態で以下を実行。

```bash
adb tcpip 5555
```

ADBがネットワーク越しの接続を受け付けるようになります。

#### 3. Tailscale経由で接続

AndroidのTailscale IPを確認して（アプリを開けば表示される）、接続。

```bash
adb connect 100.x.y.z:5555
# → connected to 100.x.y.z:5555
```

これだけ。あとは普通に `./gradlew installDebug` や `flutter run` でアプリがAndroidに入ります。

```bash
adb devices
# → 100.x.y.z:5555    device
```

### ハマったポイント

最初に `adb connect` したらタイムアウトで全然つながらなかった。原因はAndroid側のTailscaleアプリがOFFだったから。両方のデバイスでTailscaleがONになっていないとダメ、というのは当たり前なんだけど、最初は気づかなかった。

あと、Tailscale IPを間違えて別のデバイスに接続しようとしていた、というのもあった。Tailscaleアプリでデバイス一覧を見れば正しいIPがわかるので、つながらないときはまずそこを確認するのがいい。

### 注意点

- Androidを再起動すると `adb tcpip 5555` をもう一度やる必要がある
- Tailscaleは両方のデバイスでONにしておくこと
- iOSにはADBみたいな仕組みがないので、TestFlightやFirebase App Distributionで配布する形になる
- `adb tcpip 5555` を実行するとポート5555が開くが、Tailscale経由の通信はTailscaleネットワーク内に閉じているので、外部からアクセスされる心配はない。さらに安全にしたい場合は[Tailscale ACLs](https://tailscale.com/kb/1018/acls)でアクセスできるデバイスを制限できる

## CLAUDE.mdにデバイス情報を書いておく

セットアップは終わった。でも毎回IPを覚えて `adb connect` するのは面倒。ここでClaude Codeの `CLAUDE.md` が使える。

Claude Codeはプロジェクトに置いた `CLAUDE.md` を読んで、ルールや設定を理解してくれます。ここにTailscaleのデバイス情報を書いておくと便利。

```markdown
## リモートデバイス (Tailscale)
| デバイス | Tailscale IP | 用途 |
|---|---|---|
| Android端末 | 100.x.y.z:5555 | Android実機デプロイ (adb connect) |
| MacBook Pro | 100.a.b.c | 開発マシン |
```

こう書いておけば、「Androidにビルドして入れて」と言うだけで、Claude Codeが `adb connect` からデプロイまでやってくれます。TailscaleのIPは固定なので、一度書けば更新する必要もない 🙌

## 実際の開発の流れ

実際にやるとこんな感じ。iPadのターミナルからClaude Codeに話しかけるだけ。

```bash
# Claude Codeに指示
> この画面のバグ直して、Androidにデプロイして

# Claude Codeが勝手にやってくれる
$ adb connect 100.x.y.z:5555
connected to 100.x.y.z:5555

$ ./gradlew installDebug
...
BUILD SUCCESSFUL

$ adb shell am start -n com.example.app/.MainActivity
Starting: Intent { cmp=com.example.app/.MainActivity }
```

手元のAndroidでアプリが起動する。MacBookの前に座っていなくても、同じことができる。

## まとめ

使っているのは3つだけ。

| ツール | 役割 |
|---|---|
| **Jump Desktop** | iPadから自宅のMacを操作する |
| **Tailscale + ADB** | 出先のAndroidにリモートでアプリを入れる |
| **Claude Code** | コードを書いてビルド・デプロイまでやってもらう |

Tailscaleの「どのネットワークでもIPが変わらない」のが地味にうれしい。一度設定すれば、場所を気にせずAndroid実機にデプロイできる。

iPadとAndroidだけ持って出れば、どこでもネイティブアプリの開発ができる環境になりました 🎒
