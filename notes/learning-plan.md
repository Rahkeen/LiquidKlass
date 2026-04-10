# Android Backdrop Blur — Learning Plan

A structured path through the concepts behind native Android backdrop blur on API 31+.
All topics are scoped to the `RenderEffect` path. RenderScript and pre-31 fallbacks are not covered.

Reference implementation: [Haze library](https://github.com/chrisbanes/haze)
Source analysis: [blur.md](./blur.md)

---

## Phase 1: Core Primitives

These three topics are tightly coupled. Learn them together — they let you build a minimal working blur prototype by the end of Phase 1.

### 1. [GraphicsLayer — The Foundation](./topics/01-graphics-layer.md)
The off-screen GPU render target that everything else builds on. Understand how to record content into a layer, replay it, and attach effects to it.

**Goal:** Be able to capture a composable's content into a `GraphicsLayer` and replay it in a different draw scope.

### 2. [RenderEffect — The Blur Primitive](./topics/02-render-effect.md)
The API 31+ GPU-native blur API. Understand how to create a Gaussian blur, what tile modes do, and how `RenderEffect` objects are chained.

**Goal:** Attach a `RenderEffect` blur to a `GraphicsLayer` and see it render.

### 3. [DrawModifierNode and the Draw Pipeline](./topics/04-draw-modifier-node.md)
How a `Modifier.Node` hooks into the Compose draw pass. Understand `ContentDrawScope`, draw ordering, and why `Modifier.Node` is the right tool over `Modifier.composed`.

**Goal:** Write a `DrawModifierNode` that draws a blurred layer beneath a composable's own children.

**Checkpoint:** By end of Phase 1, you should be able to reproduce the minimal blur prototype from `blur.md §Building Your Own`.

---

## Phase 2: Architecture

How the source and effect sides of blur communicate and stay in sync.

### 4. [The Two-Node Architecture — Source vs. Effect](./topics/03-two-node-architecture.md)
The design pattern that allows blur to work when the background content and the blur overlay are separate, unrelated parts of the composition tree.

**Goal:** Understand why shared state (`HazeState`) is necessary and how data flows from source node to effect node.

### 5. [Position Tracking](./topics/05-position-tracking.md)
How the effect node determines which portion of the background it's overlaying. Covers `positionInRoot()`, `positionOnScreen()`, and the auto-promotion strategy for cross-window scenarios.

**Goal:** Correctly compute and apply the position offset so the blurred content matches what's physically behind the overlay.

---

## Phase 3: Performance

Two independent optimizations that matter in production.

### 6. [Input Scaling](./topics/06-input-scaling.md)
Why full-resolution input is wasteful for blur, how Haze scales the captured layer down before blurring, and how to scale the blur radius and position offset accordingly.

**Goal:** Add input scaling to your implementation and understand the tradeoff at different scale factors.

### 7. [RenderEffect Caching](./topics/09-render-effect-caching.md)
`LruCache` pattern for `RenderEffect` objects. Why construction is expensive, how to key the cache correctly, and where to scope it.

**Goal:** Add an `LruCache` to your implementation and verify that `RenderEffect` objects are not recreated on identical parameters.

---

## Phase 4: Polish

The details that turn a blur into frosted glass, and the plumbing that keeps it correct at runtime.

### 8. [RenderEffect Composition — Building Frosted Glass](./topics/07-render-effect-composition.md)
Layering Gaussian blur + noise texture + color tint + alpha mask into a single GPU pipeline using chained `RenderEffect` objects.

**Goal:** Implement the full frosted glass pipeline and understand what each layer contributes visually.

### 9. [Dirty Tracking & Invalidation](./topics/08-dirty-tracking.md)
Why recomposition alone doesn't keep blur current when background content scrolls, and how `ViewTreeObserver.OnPreDrawListener` bridges the Android frame loop with Compose's draw pipeline.

**Goal:** Make blur correctly update when a `LazyColumn` scrolls behind it, without unnecessary per-frame invalidations.

---

## Suggested Order at a Glance

```
Phase 1 (Core)         Phase 2 (Architecture)    Phase 3 (Perf)       Phase 4 (Polish)
──────────────         ──────────────────────    ──────────────       ────────────────
01 GraphicsLayer   →   03 Two-Node Arch      →   06 Input Scaling →   07 RenderEffect
02 RenderEffect    →   05 Position Tracking  →   09 Caching       →       Composition
04 DrawModifier                                                      →   08 Dirty Tracking
```

---

## Topics Not Covered Here

- **Progressive / gradient blur** — requires additional API 33+ `RuntimeShader` knowledge or the multi-layer approximation for API 31–32
- **RenderScript fallback (pre-31)** — deprecated path, intentionally excluded
- **Scrim fallback** — color-only degradation, no blur
- **Multi-window / Dialog blur** — the Screen position strategy is introduced in Topic 5 but cross-window edge cases are not fully explored
