---
title: "OpenClawでUnity自律開発を試したが、正直まだ厳しいと感じた話"
emoji: "🐾"
type: "tech"
topics: ["openclaw", "unity", "ai", "hackathon", "gamedev"]
published: true
---

## やろうとしたこと

ハッカソンの機会に、話題の[OpenClaw](https://github.com/OpenClawGroup/OpenClaw)でUnityゲームを作ろうとした。

普段はClaude CodeのSubagentで並行開発を回していて、それなりにうまくいっている。だったらOpenClawにオーケストレーションごと任せたらどうなるのか。コードも画像も音楽も全部AI、人間はディレクションだけ。どこまでいけるか試した。

厳しかった。期待が大きかった分、落差がきつい。

## 構成と準備

作ったのはGlitchClawという2Dサバイバーズライク。サイバーパンクな世界で小さいクリーチャーが敵と10分間戦い続けるやつ。

https://github.com/rioX432/roguelike-survivor

| 要素 | 技術 |
|------|------|
| エンジン | Unity 6（URP 2D） |
| AIオーケストレーション | OpenClaw 2026.2.21 |
| 画像生成 | [ComfyUI MCP](https://github.com/joenorton/comfyui-mcp-server)（ローカル） |
| 音楽 | Suno via MCP |
| Unity操作 | [Unity MCP](https://github.com/CoderGamester/mcp-unity) |
| 人間とのやりとり | Telegram Bot |

当初の想定では、人間はTelegramでOpenClawとやりとりするだけで、裏で勝手に開発が進む流れだった。仕様の指示や修正依頼をTelegramに投げたら、OpenClawが受け取って開発を回して、結果を返してくれる。そういう世界観。

OpenClawには4つのサブエージェントを置いた。Architectがタスク分解とプロジェクト管理、Builder-Alphaがプレイヤー操作とUI、Builder-Betaが敵システムとデータ構造、Reviewerがコード品質チェック。

![OpenClawのオーケストレーション構成](/images/openclaw-architecture.png)

OpenClawはローカルで動く自律エージェントで、ファイル操作やコマンド実行の権限を持つ。セキュリティリスクを最小限にするために、新規Macユーザープロファイルをデータ空の状態で作り、APIキーはこの検証専用のClaude API Keyを発行し、Docker Desktopでサンドボックス化した環境でのみOpenClawを実行した。既存の開発環境や認証情報には一切触れさせていない。

## 並行PR生成は確かにすごい

午前中にセットアップを済ませてOpenClawを起動。ゲームの仕様をざっくり伝えたら、サブエージェントが並行で動き出した。

| PR | 内容 |
|----|------|
| #1 | PlayerController, PlayerStats, VirtualJoystick, CameraFollow |
| #2 | GameDataGenerator EditorScript |
| #3 | InfiniteMapController（3x3チャンクのリングバッファ） |
| #4 | EnemyBase（追跡AI、接触ダメージ、IPoolable） |
| #5 | EnemySpawner と XPGem |
| #7-#8 | AttackModule（Projectile, Radial, Cone, Homing） |
| #9 | PlayerXP, LevelUpPanel, UpgradeCardUI |
| #10-#11 | HUD, ResultScreen, AudioManager |

13PR生成、12件マージ。粒度もそこまで悪くない。プレイヤー操作、敵システム、攻撃モジュール、UIと機能単位でちゃんと分かれている。

ここだけ見ると、AIチームで並行開発って成立するんだな、と素直に感心した。

ただ、Unity開発はここからが本番だった。

## 「完成しました」と言われて見た現実

昼過ぎ、OpenClawから完成報告が来た。

Unity Editorで開いてPlayを押す。頼む動いてくれ、と祈りながら待つ。

真っ青な画面。

![OpenClawが完成と報告した直後のゲーム画面](/images/openclaw-blue-screen.jpg =250x)
*完成報告の直後に見た画面がこれ*

GameObjectが配置されていない。画像は未生成。音楽もない。C#のコンパイルは通っているし、Prefabの定義もある。けどシーン上に何もない。ビルドが通るだけで、ゲームとしては成り立っていなかった。

コードを書くことと、Unityでゲームを動かすことは全然違う。それを目の前に突きつけられた感じだった。

## なぜ噛み合わなかったのか

ここから残り時間はバグ修正に費やした。その過程で、OpenClawと自分の開発スタイルのミスマッチがはっきり見えてきた。

### フィードバックループが長すぎる

フィードバックループは短いほどいい。これはUnityに限った話じゃなくて、開発全般に言える。ただ今回は自分がUnityの経験が浅いので、特にスモールに回したかった。大きく変更が入ると、どこが原因で壊れたのか自分では追えない。小さく変えて、確認して、また小さく変える。そのサイクルでやりたかった。

OpenClaw経由だとこのサイクルがまるで回らない。そもそもTelegramにメッセージを送っても、返答が全然来ない。Telegramで指示を出して裏で勝手に開発が進む想定だったのに、投げたメッセージに対して沈黙が続く。今どうなっているのか、受け取ったのかすらわからない。結局しびれを切らしてOpenClawのログを直接見に行く、という本末転倒な流れになった。

![TelegramでのOpenClawとのやりとり](/images/openclaw-telegram.jpg =250x)
*たまに返ってくると思ったら、タイムスタンプが全部14:32。一方的な進捗報告の連投だった*

バグ報告をしてから修正が戻ってくるまで、体感で1時間近くかかったケースもあった。Claude Codeでの即確認のテンポとは比べものにならない。

マージ後のコミットログを見ると、修正の連続になっている。

```
fix: LevelUpSystem freeze - queue-based levelup, failsafe 8s auto-resume
fix: retry button blocked by swipe overlay
fix: explicit sortingOrder - player=10, enemies=2, projectiles=3
fix: Enemy tag error (freeze cause), TMP->UI.Text migration
fix: extract single frame from sprite sheets for cleaner in-game display
```

レベルアップでフリーズ、ボタンがオーバーレイの下に隠れる、描画順がバラバラ、スプライトシートの切り出しミス。全部Playして初めてわかるバグで、コードレビューでは拾えない。結局、人間が目で確認して1つずつ報告して直す作業に大半の時間を使った。

### 外部ツールの障害に気づけない

ComfyUIが途中でコケていたのに、OpenClawはそれに気づかず最後まで走り切った。人間がUnity Editorで確認して初めて、画像が生成されていないことがわかった。

Claude Codeなら、コマンドの実行結果を逐次見て「あ、これ失敗してるな」と気づける。OpenClawにはその確認ポイントがない。最後に蓋を開けて、壊れていたことを知る。

### 中で何をやっているか見えない

OpenClawが内部でどうClaudeを呼んでいるのか見えない。調査はしているのか、計画は立てているのか、APIを何回叩いたのか、トークンをどれだけ食っているのか。

Claude Codeなら考えている内容が逐次見えるし、Subagentに何を委譲したかもわかる。OpenClawだと「よくわからないけど待つしかない」という時間がひたすら続く。修正と確認を即座に繰り返すUnity開発で、この不透明さはかなりきつい。

### バグを潰して動いた最終状態

1つずつバグを潰して、ようやくゲームとして動くようになったのが夕方。OpenClawの自律開発よりも、人間のデバッグ作業の方がはるかに時間を食った。

![バグ修正後の最終的なゲームプレイ](/images/openclaw-gameplay.gif)
*ここまで持ってくるのに何時間かかったか*

## 普段の開発スタイルとの比較

普段のモバイル開発では、Claude CodeのSubagentで自分がオーケストレーションしている。tmuxで8ペイン並べて、チケットIDを渡すだけでPRまで走る仕組みだ。

| | Claude Code + 人間がオーケストレーション | OpenClaw |
|---|---|---|
| フィードバック速度 | 即時 | 遅い（タスク単位のバッチ） |
| 中間状態の確認 | 逐次確認できる | ブラックボックス |
| エラーハンドリング | 即座に方向修正 | 気づかず走り切ることがある |
| 並行度 | 手動で管理 | 自動で複数エージェント |
| オーケストレーションの柔軟性 | 状況に応じて判断 | 事前定義のフロー |

OpenClawが勝っているのは並行度の自動化。手動で管理しなくても、勝手にサブエージェントを立てて走ってくれる。ただ、それ以外の部分ではClaude Codeを自分で操作した方が速かったし正確だった。

自分で複数のClaude Codeインスタンスを立てて、自分がオーケストレーションした方が速い。今の段階ではそういう結論になる。

## 改善されたらまた試したいポイント

文句だけ言って終わるのはフェアじゃないので、ここが変わったらまた試したい、というポイントも書いておく。

Unity Editorとのフィードバックループ統合。Play結果のスクリーンショットやログを自動で取得してエージェントに戻す仕組みがあれば、話はだいぶ変わる。Mobile MCPのようなアプローチがUnity Editor向けにあればいい。

中間状態の可視化。各エージェントが今何をやっているか、APIコールの回数やトークン消費がリアルタイムで見えるダッシュボードが欲しい。

外部ツール障害の検知。ComfyUIが落ちたときに自動で気づいてリカバリするか、少なくとも人間に通知する仕組み。

OpenClawの進化は速いし、[OpenAIに買収された](https://www.leanware.co/insights/openai-openclaw-acquisition)ことで開発リソースも増えるはず。半年後にはまた違う話になっているかもしれない。

## まとめ

OpenClawの並行PR生成は成立していた。13PRが自動で出てきて、粒度も悪くなかった。ただUnity開発との相性は悪い。作ってPlayして直すループが回らず、フィードバックは遅く、中間状態も見えず、外部ツールの障害にも気づけない。1日のハッカソンの大半を、Playしてバグを見つけて報告する作業に使った。

もっと時間をかけて設定を詰めれば違う結果になるかもしれない。ただ今回の感触では、Unity自律開発にそのまま適用するのは難しかった。

## 参考

- [OpenClaw GitHub](https://github.com/OpenClawGroup/OpenClaw)
- [Unity MCP](https://github.com/CoderGamester/mcp-unity)
- [ComfyUI MCP](https://github.com/joenorton/comfyui-mcp-server)
