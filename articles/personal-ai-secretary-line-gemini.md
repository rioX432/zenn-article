---
title: "毎朝LINEに情報を届けるAI秘書を作った — 使うほど好みを学習する仕組み"
emoji: "📬"
type: "tech"
topics: ["line", "gemini", "vercel", "githubactions", "mastra"]
published: false
---

## 追うべきトピックが増え続けている

AIエージェントやVTuber技術のトレンドを日常的に追っているんですが、ここ最近は本当にペースが速い。AIエージェント自体の普及によって技術の進化速度が加速していて、少し気を抜いただけで重要な動きを見逃すようになってきた。

しかも厄介なのは、新しい知見が入るほど「次に追うべきトピック」も増えていくこと。OpenClaw、PicoClaw、Mobile MCP、FastBot……気づいたら監視対象が雪だるま式に膨らんでいる。RSS、X、Discord……情報ソースが増えるほど、巡回にかかる時間も増えていく一方だった。

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

実際にこんな感じで届く。

![LINEに届いたデイリーダイジェスト](/images/ai-secretary-line-screenshot.png)

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

## コスト

| 項目 | コスト |
|---|---|
| Gemini 2.5 Flash | 無料枠内（現状） |
| LINE Messaging API | 月200通無料（31日 × 2通 = 余裕） |
| GitHub Actions | 月2,000分無料（1回1〜2分 × 31日 = 余裕） |
| Vercel | 無料枠内 |
| mem0 | 無料枠内 |

全部無料枠でまかなえている。個人用途で使う分にはしばらく大丈夫そうだけど、フィードバックが積み上がってmem0の読み書きが増えてきたら有料プランへの切り替えを検討するつもり。

今日デプロイしたばかりなので、フィードバックループがどのくらいのペースで効いてくるかはこれから検証します。1〜2週間使い込んだら、スコアリングの変化をまた記事にまとめたい。

## Claude Codeで数時間で作れた

この規模のBotを一から作るとなると、以前なら週末1〜2日はかかっていたと思う。今回はClaude Codeを使いながら開発を進めて、構想から動作確認まで数時間で完結した。

RSS取得・スコアリング・LINE通知・Webhookサーバー・メモリ基盤、それぞれの実装をClaude Codeに任せつつ、自分は「何を作るか」「どう組み合わせるか」の判断に集中できた。コードを書く時間よりも、構成を考える時間の方が長かった気がする。

「AIエージェントが技術の進化を加速させている」と書いたけれど、それは自分の開発体験にも当てはまっていた。今回作ったBotで学習の効率化を図りながら、その開発自体もAIで効率化されている。道具が道具を作る時代になってきたなと改めて感じた。
