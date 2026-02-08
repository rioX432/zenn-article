# CLAUDE.md

Zenn記事リポジトリ。Zenn CLIで管理し、GitHub連携でzenn.devにデプロイされる。

## コマンド

```bash
# プレビュー（ポート競合時は自動で別ポートになる）
npx zenn preview

# 記事作成
npx zenn new:article --slug my-slug --title "Title" --type tech --emoji "🚀"

# 本作成
npx zenn new:book
```

## ディレクトリ構成

- `articles/` — 記事のMarkdownファイル
- `books/` — 本のディレクトリ
- `images/` — 記事で使う画像（3MB以下にすること）

## 記事のフォーマット

```yaml
---
title: "記事タイトル"
emoji: "😸"
type: "tech"  # or "idea"
topics: ["tag1", "tag2"]  # 最大5つ
published: false  # trueで公開
---
```

- slugは a-z0-9, ハイフン, アンダースコアのみ、12-50文字

## 画像の扱い

- `images/` ディレクトリに配置
- **3MB以下** にすること（超えるとデプロイエラー）
- 大きい画像は `sips --resampleWidth 1400 画像パス --out 画像パス` でリサイズ
- 記事内では **絶対パス** で参照: `![alt](/images/filename.png)`
- 相対パス（`../images/`）は使わない

## 埋め込み

- **Xポスト**: URLをそのまま書くだけ（前後に改行を入れる）。`@[tweet](URL)` は古い記法
- **YouTube**: `@[youtube](動画ID)`
- **GitHub Gist**: `@[gist](URL)`

## 記事の文体ルール

- AIっぽくない自然な口語体で書く
- 難しい横文字や堅い表現は避ける
- セクションタイトルに絵文字を全部つけるのはAIっぽいのでNG
- 文中にわずかに絵文字を散らすのはOK
- 試行錯誤やハマったポイントを入れると人間味が出る
- 「まず」「次に」「最後に」の多用は機械的に見えるので避ける

## デプロイ

- GitHubにPushすると自動デプロイ
- `[ci skip]` をコミットメッセージに入れるとデプロイをスキップ

## コミット

- 英語、1行、簡潔
- AIスタンプ・Co-Authored-Byは不要

## 言語

- 会話: 日本語
- コミットメッセージ: 英語
