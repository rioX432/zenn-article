---
title: "Compose の ModalBottomSheet で Snackbar が隠れる問題と Dialog overlay による解決"
emoji: "📱"
type: "tech"
topics: ["android", "jetpackcompose", "kotlin", "material3"]
published: true
---

:::message
**TL;DR**: `ModalBottomSheet` は内部で別ウィンドウを作るため、`SnackbarHost` が裏に隠れる。Snackbar も別 `Dialog` で表示して最前面に持ってくるワークアラウンドを紹介する。制約あり。
:::

**検証環境**:  Compose BOM 2024.12.01 / Material3 1.3.1 / Android 15-16（Pixel 8, Sony Xperia 1 VI, Samsung Galaxy S25 Ultra）

## 何が起きるか

本番でミュート操作の Snackbar が表示されないという QA レポートが上がって調べたら、`ModalBottomSheet` を開いた状態だと Snackbar がシートの裏に隠れていた。

`Modifier.zIndex()` を盛っても無意味。`Scaffold` の `snackbarHost` パラメータを使っていても同じ。

Google の Issue Tracker にも報告されているけど、2025年12月時点でステータスは "Not started"（最新状況は [Issue #377543106](https://issuetracker.google.com/issues/377543106) を確認してください）。

https://issuetracker.google.com/issues/377543106

ちなみに Flutter でも[同じ問題が報告されている](https://github.com/flutter/flutter/issues/75968)ので、プラットフォーム関係なくモーダル系 UI の宿命っぽい。

## なぜ起きるのか

原因は `ModalBottomSheet` の内部実装にある。

M3 の `ModalBottomSheet` は内部で Compose の `Dialog` composable を使っている。`Dialog` は Android の新しい `Window`（OS レベルの描画レイヤー）を作る。つまりシートが表示されると、アプリのメインウィンドウとは**別のウィンドウ**が前面に生成される。

```
[Window 1] Activity のメインウィンドウ
  └── Scaffold
       ├── SnackbarHost  ← ここにいる
       └── Content

[Window 2] ModalBottomSheet の Dialog ウィンドウ  ← こっちが上
  └── Sheet content
```

`SnackbarHost` はメインウィンドウ（Window 1）にいるので、別ウィンドウである ModalBottomSheet（Window 2）の下に隠れる。ウィンドウが違うので `Modifier.zIndex()` は効かない。

## よくあるワークアラウンドと限界

### 1. ModalBottomSheet 内に SnackbarHost を置く

```kotlin
ModalBottomSheet(onDismissRequest = { ... }) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        // Sheet content
    }
}
```

動くけど、シート内にしか Snackbar が表示されない。しかもシートを開くたびに `SnackbarHost` を仕込む必要がある。アプリ内にモーダルが10個あったら10箇所に書くことに…。

### 2. Toast を使う

Snackbar を諦めて `Toast` にする。デザインの自由度ゼロ。Material Design ガイドラインとも合わない。

### 3. Material 2 の ModalBottomSheetLayout を使う

M2 の `ModalBottomSheetLayout` は別ウィンドウではなく同じ Composition 内に描画するので問題が起きない。でもそのために M2 に留まるのは本末転倒。

### どれを選ぶか

| 方式 | Snackbar の表示位置 | メンテ負荷 | アクションボタン | 制約 |
|---|---|---|---|---|
| Sheet 内に SnackbarHost | シート内のみ | 高（モーダル毎に設置） | 使える | シート閉じると消える |
| Toast に逃げる | システム Toast | 低 | 使えない | デザイン自由度ゼロ |
| M2 に留まる | 通常通り | 低 | 使える | M3 に移行できない |
| **Dialog overlay（後述）** | **画面全体の最前面** | **低（1箇所）** | **使えない** | **OEM 差に注意** |

アクション付き Snackbar が不要で、アプリ全体で統一的に使いたいなら Dialog overlay が一番手軽だった。

## 解決策: Dialog overlay

発想を変えて、**Snackbar も別の Dialog で表示する**。

Android の `Dialog` は後から作られたものが上に来る。だから ModalBottomSheet の Dialog よりあとに Snackbar 用の Dialog を作れば、Snackbar が上に表示される。

```
[Window 1] Activity
[Window 2] ModalBottomSheet
[Window 3] Snackbar Dialog  ← 最前面
```

ポイントは Window 3 がタッチを一切消費しないようにすること。

### 完全なコード

```kotlin
@Composable
fun SnackbarOverlayDialog(
    snackbarHostState: SnackbarHostState,
) {
    // Snackbar が無いときは Dialog を出さない
    if (snackbarHostState.currentSnackbarData == null) return

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,  // Dialog を画面幅いっぱいに広げる
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,   // システムバー領域も含めて描画
        )
    ) {
        // DialogWindowProvider: Compose の Dialog が内部で持つ Android Window への参照を取得する
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.apply {
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
```

### 使い方

`Scaffold` の `snackbarHost` は空のままにして、`content` の中で `SnackbarOverlayDialog` を呼ぶ。

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

Scaffold { innerPadding ->
    // メインコンテンツ
    MainContent(modifier = Modifier.padding(innerPadding))

    // Snackbar overlay（ModalBottomSheet より後に配置）
    SnackbarOverlayDialog(snackbarHostState = snackbarHostState)
}
```

:::message
`Scaffold` の `snackbarHost` パラメータに `SnackbarHost` を渡してしまうとメインウィンドウ側に描画されるため、解決にならない。必ず `content` ブロック内で `SnackbarOverlayDialog` を使うこと。
:::

## `FLAG_NOT_FOCUSABLE` だけでは不十分な理由

ここがハマりポイントだった。

最初は `FLAG_NOT_FOCUSABLE` だけで十分だと思ってた。`FLAG_NOT_FOCUSABLE` は `FLAG_NOT_TOUCH_MODAL` を暗黙的に含むので、タッチがパススルーされるはず…と思いきや、パススルーされない。

理由はこう:

- `FLAG_NOT_TOUCH_MODAL` は **Window の外部**へのタッチだけをパススルーする
- `usePlatformDefaultWidth = false` + `fillMaxSize()` で Dialog は画面全体を覆っている
- 画面全体が Window の「内部」なので、全タッチが Dialog に吸われる

`FLAG_NOT_TOUCHABLE` を追加すると、Window 自体がタッチイベントを一切受け取らなくなるので、すべてのタッチが下のウィンドウに透過する。

```kotlin
// ❌ これだけだとタッチが透過しない
setFlags(
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
)

// ✅ FLAG_NOT_TOUCHABLE も必要、FLAG_DIM_BEHIND も消す
clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
addFlags(
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
)
```

## 注意点

実運用で踏んだ問題を重要度順に並べている。

### 【致命的】OEM によっては `FLAG_DIM_BEHIND` がタッチをブロックする

Compose の `Dialog` はデフォルトで `FLAG_DIM_BEHIND` を設定する。Pixel 等では `setDimAmount(0f)` だけで問題ないが、**Sony Xperia 等の一部 OEM では dim surface がタッチを `FLAG_NOT_TOUCHABLE` とは独立して横取りする**。

`clearFlags(FLAG_DIM_BEHIND)` を明示的に呼ばないと、Snackbar の下にあるボタンやシートが一切タップできなくなる。実際に本番でこの問題に遭遇した。

また `LaunchedEffect` ではなく `SideEffect` を使っている理由は、リコンポジション毎にフラグを確実に適用するため。一部デバイスで Compose がウィンドウフラグをリセットするケースがあった。

### 【要考慮】Snackbar のアクションボタンがタップできない

`FLAG_NOT_TOUCHABLE` を設定しているので、Snackbar 自体もタップできない。「元に戻す」ボタン付きの Snackbar を使いたいケースでは注意。

対策としては:
- Snackbar 表示中だけ `FLAG_NOT_TOUCHABLE` を外し、Snackbar 外のタッチを手動で下のウィンドウに転送する
- もしくは、アクション付き Snackbar が必要な画面では別のアプローチを使う

今のところアクション付き Snackbar を使う場面がないので、この制約は許容している。

### 【軽微】表示順の競合

Snackbar 表示中に新しい `ModalBottomSheet` が開かれると、その Dialog が Snackbar Dialog より上に来る可能性がある。ただ Snackbar は通常3秒で消えるので、実用上は問題にならなかった。

## サンプルアプリ

Before（通常の Scaffold SnackbarHost）と After（Dialog overlay）を切り替えられるデモアプリを用意した。

![デモ](/images/snackbar-overlay-demo.gif)

https://github.com/user/zenn-article/tree/main/articles/compose-snackbar-above-modalbottomsheet

:::details MainActivity.kt（折りたたみ）
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SnackbarDemo()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnackbarDemo() {
    var useOverlay by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = {
            // Before: 通常の SnackbarHost（ModalBottomSheet の裏に隠れる）
            if (!useOverlay) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (useOverlay) "After: Dialog Overlay" else "Before: Scaffold SnackbarHost",
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use Dialog Overlay")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = useOverlay, onCheckedChange = { useOverlay = it })
            }

            Button(onClick = { showSheet = true }) {
                Text("Show BottomSheet")
            }

            Button(onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("Hello from Snackbar!")
                }
            }) {
                Text("Show Snackbar")
            }
        }

        // After: Dialog overlay（ModalBottomSheet より上に表示される）
        if (useOverlay) {
            SnackbarOverlayDialog(snackbarHostState = snackbarHostState)
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = "ModalBottomSheet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("この状態で Snackbar を表示してみてください")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Snackbar while sheet is open!")
                    }
                }) {
                    Text("Show Snackbar")
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SnackbarOverlayDialog(
    snackbarHostState: SnackbarHostState,
) {
    if (snackbarHostState.currentSnackbarData == null) return

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.apply {
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
```
:::

## もっと良い方法があれば教えてください

この Dialog overlay 方式はあくまでワークアラウンドで、最適解だとは思っていない。`FLAG_NOT_TOUCHABLE` でアクションボタンが押せなくなる制約もあるし、OEM ごとの挙動差も踏むと正直キレイな解決策とは言いづらい。

同じ問題で困っていて、もっと良いアプローチを見つけた方がいたらぜひコメントで教えてほしいです。

## 参考

- [Google Issue Tracker #377543106 - Clarity for how snackBar and modal bottom sheet should work together](https://issuetracker.google.com/issues/377543106)
- [Drawing Custom Alerts on Top of Bottom Sheets - Sanskar's Blog](https://blog.sanskar10100.dev/drawing-custom-alerts-on-top-of-bottom-sheets-in-jetpack-compose)
- [Kotlin Slack - ModalBottomSheet snackbar discussion](https://slack-chats.kotlinlang.org/t/18840056/)
- [Zenn - ModalBottomSheetの裏にSnackbarが隠れてしまう問題](https://zenn.dev/tick_taku/scraps/9264f78457dd56)
- [Flutter #75968 - Same issue in Flutter](https://github.com/flutter/flutter/issues/75968)
