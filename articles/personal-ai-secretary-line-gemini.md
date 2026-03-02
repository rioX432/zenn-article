---
title: "毎朝LINEに情報を届けるAI秘書を作った — 使うほど好みを学習する仕組み"
emoji: "📬"
type: "tech"
topics: ["line", "gemini", "vercel", "githubactions", "mastra"]
published: false
---

## 情報収集が面倒になってきた

RSSリーダー、Hacker News、GitHub Trending、Zenn……毎日チェックするのに15〜20分かかっていた。しかも大半は自分に関係ない記事で、「今日も収穫なし」で終わることが多い。

いっそ自分好みの記事だけ届けてくれる仕組みを作ってしまおうと思い立った。毎朝LINEに通知が来て、ボタン一つでフィードバックができて、それが翌日の精度に反映される——そんなBotを作った話です。

## 全体の流れ

```
情報収集（RSS / HN / GitHub Trending）
    ↓
Gemini 2.5 Flash でスコアリング
    ↓
LINE Push Message で通知
    ↓
Quick Reply でフィードバック（helpful / not_interested / bookmark）
    ↓
Vercel Webhook で受信 → mem0 に保存
    ↓
翌日のスコアリングに反映
```

GitHub Actions で毎朝7時に自動実行。実装はNode.js 22 + TypeScript（ESM）で、[Mastra](https://mastra.ai/)というTypeScript製AIフレームワークを使ってGeminiとの接続やエージェントの設定を書いています。

ランニングコストは月$0.10以下を目標にしているので、LLMはGemini 2.5 Flash（無料枠あり）を選んでいます。

## 情報収集 — 3ソースを並行取得

毎朝、3つのソースから並行してデータを取ってきます。

**RSS**: `data/interests.yaml` にフィードURLを書いておくと全部まとめて取得。Zennのトピックフィードや好きな技術ブログを登録している。

**Hacker News**: [HN API](https://hacker-news.firebaseio.com/v0/) の `topstories` から上位100件を取得して、スコア閾値（デフォルト100点）でフィルタリング。

**GitHub Trending**: スクレイピングで今日のトレンドリポジトリを取得。言語フィルターでKotlin/Swift/Python/TypeScriptのみに絞っている。

3ソース合計で200〜400件くらいのアイテムが集まる。これをそのままLINEに送るのは無理なので、ここからAIで絞り込む。

## スコアリング — Geminiに採点させる

`data/interests.yaml` に自分の興味プロファイルを定義しています。

```yaml
topics:
  - name: KMP (Kotlin Multiplatform)
    weight: 1.0
    keywords: ["KMP", "Kotlin Multiplatform", "Compose Multiplatform"]
  - name: AI/LLM
    weight: 0.9
    keywords: ["LLM", "Claude", "Gemini", "RAG", "fine-tuning"]
  - name: VTuber技術
    weight: 0.8
    keywords: ["Live2D", "VTuber", "AITuber", "音声合成"]
```

これをGeminiに渡して、各記事を0〜1でスコアリングしてもらう。プロンプトにはフィードバック履歴も含めるので、「先週これが役立った」「これは興味なかった」という情報がスコアに影響する。

記事選定のロジックはちょっと工夫していて、高スコア記事が多い日は多めに送り、少ない日は最低件数を保証するようにしています。

## LINE通知 → Quick Reply → Webhook

記事一覧をLINE Pushで送った直後に、「今日の記事、参考になりましたか？」という別メッセージを送る。3択のQuick Replyボタンがついている。

```
[参考になった 👍] [興味なし 👎] [あとで読む 🔖]
```

タップするとPostbackイベントがサーバーに飛んでくる。ここで問題が出た。**GitHub Actionsはバッチ実行専用なので、Postbackを受け取るには常時起動のサーバーが必要**になる。

Vercel Serverless Functionsで対応しました。`api/line-webhook.ts` を置くだけでエンドポイントが生えるのが手軽。

```typescript
// Vercelの自動bodyパースを無効化（HMAC検証に生のbodyが必要）
export const config = { api: { bodyParser: false } };

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const rawBody = await new Promise<string>((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
  const signature = req.headers['x-line-signature'] as string ?? '';
  const result = await handleLineWebhook(rawBody, signature);
  res.status(result.status).send(result.body);
}
```

`bodyParser: false` は必須。デフォルトだとVercelが自動でbodyをパースしてしまい、HMAC-SHA256の署名検証に使う生のbodyが取れなくなる。

受け取ったフィードバックはmem0に保存する。

```
helpful → "Compose Multiplatform 1.8リリース — 新しいNavigation APIの使い方"
not_interested → "量子コンピューティング入門"
```

翌日のスコアリング時に過去14日分のフィードバックをGeminiのプロンプトに含める。「役立った」記事と似たものはスコアが上がり、「興味なし」と近いものは下がっていく。

## ハマったポイント

**Vercel runtimeの指定でコケた。** 最初の `vercel.json` に `"runtime": "nodejs22.x"` と書いたら「Function Runtimes must have a valid version」エラーが出た。Node.jsはVercelのビルトインなので `runtime` フィールド自体が不要で、`vercel.json` を丸ごと消したらあっさり動いた。

**LINE署名検証で401。** `LINE_CHANNEL_SECRET` をVercel環境変数に間違えた値で登録していた。LINE DevelopersのBasic settingsにある「Channel secret」とMessaging APIの「Channel access token」は別物で、署名検証に使うのは前者。混同しがち。

**`vercel env add` はlinkが先。** `vercel env add` を実行したら「まずプロジェクトにlinkしてから」と怒られた。`vercel link` → `vercel env add` → `vercel deploy --prod` の順番を守る必要がある。

## コスト

| 項目 | コスト |
|---|---|
| Gemini 2.5 Flash | 無料枠内（現状） |
| LINE Messaging API | 月200通無料（31日 × 2通 = 余裕） |
| GitHub Actions | 月2,000分無料（1回1〜2分 × 31日 = 余裕） |
| Vercel | 無料枠内 |
| mem0 | 無料枠内 |

全部無料枠でまかなえている。個人用途で使う分にはしばらく大丈夫そうだけど、フィードバックが積み上がってmem0の読み書きが増えてきたら有料プランへの切り替えを検討するつもり。

## コードはOSSで公開中

https://github.com/rioX432/personal-ai-secretary

`data/interests.yaml` の興味プロファイルを書き換えるだけで自分用にカスタマイズできます。セットアップ手順はREADMEに書いています。

今日デプロイしたばかりなので、フィードバックループがどのくらいのペースで効いてくるかはこれから検証します。1〜2週間使い込んだら、スコアリングの変化をまた記事にまとめたい。
