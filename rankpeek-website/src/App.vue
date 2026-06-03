<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

const githubUrl = 'https://github.com/wxl11071123/rankpeek-rebuild'
const releaseUrl = `${githubUrl}/releases/tag/v1.0.0`
const downloadUrl = 'https://rankpeek-downloads.cn-nb1.rains3.com/RankPeek%20Setup%201.0.0.exe'
const qqGroup = '598234692'
const qqGroupUrl = 'https://qm.qq.com/q/NoCaoWF6GC'

const assetUrl = (fileName: string) => `/assets/${fileName}`

const assets = {
  logo: assetUrl('rankpeek-logo.png'),
  heroGlow: assetUrl('rankpeek-white-glow.png'),
  pregame: assetUrl('rankpeek-pregame-analysis-safe.png'),
  opggList: assetUrl('rankpeek-opgg-list-safe.png'),
  opggDetail: assetUrl('rankpeek-opgg-detail-safe.png'),
  rpIndex: assetUrl('rankpeek-rp-index-safe.png'),
  postgameReview: assetUrl('rankpeek-postgame-review-safe.png'),
  postgamePraise: assetUrl('rankpeek-postgame-praise-safe.png'),
  coachReport: assetUrl('rankpeek-coach-report-safe.png')
}

const storyScenes = [
  {
    id: 'pregame',
    step: '01',
    label: '赛前',
    eyebrow: '赛前风险提示',
    title: '游戏开始，\n看清局势',
    body:
      '对战信息页会展示队友/对手数据信息，可以看最近历史战绩，同时有标签和 AI 分析帮助判断。',
    note:
      '选择英雄后自动切换到玩家使用该英雄的样本，并与段位平均水平比较。AI 分析仅适用于排位模式。',
    points: [],
    visual: 'pregame'
  },
  {
    id: 'opgg',
    step: '02',
    label: '数据聚合',
    eyebrow: '数据聚合',
    title: '数据聚合',
    body: '自动识别玩家段位和选择英雄展示 OPGG公开数据，使用缓存技术保证体验。',
    note: '支持跟随当前对局，也可以手动搜索英雄、切换榜单、段位和分路。',
    points: ['跟随当前对局', '榜单与英雄详情', '本地缓存提速'],
    visual: 'opgg'
  },
  {
    id: 'rp',
    step: '03',
    label: 'RP 指数',
    eyebrow: 'RP 指数',
    title: '内置数据分析：RP指数',
    body:
      'RP 指数基于时间线计算，使用经济、等级、CS、击杀参与、死亡、关键资源、视野，并结合分路对位、团队占比、发育节奏和低有效参与惩罚。多因素结合计算，比传统KDA评分更全面更合理。',
    note: '排位模式适用。',
    points: [],
    visual: 'rp'
  },
  {
    id: 'postgame-ai',
    step: '04',
    label: '赛后',
    eyebrow: '赛后',
    title: '赛后复盘/夸夸机',
    body:
      'AI给对局所有玩家进行分析，客观排名从夯到拉，并进行总结。或者给你来一段彩虹屁，提供满满的情绪价值。',
    note: '赛后复盘和夸夸机报告都会保存在本地 AI 报告记录中。',
    points: ['客观排名', '对局总结', '彩虹屁'],
    visual: 'postgameAi'
  },
  {
    id: 'coach',
    step: '05',
    label: '电子教练',
    eyebrow: '电子教练',
    title: 'AI复盘最近二十局排位',
    body:
      '电子教练从本地数据库读取对局记录，补齐详情和时间线，汇总各个数据，再生成近期复盘报告。',
    note: '目标是帮助用户快速看清最近一段时间的稳定模式和重复问题。',
    points: ['最近 20 局排位', '本地记录 + 详情/时间线', '近期复盘报告'],
    visual: 'coach'
  }
]

const faqItems = [
  {
    question: 'RankPeek（以下称 RP）对我的账号安全吗？',
    answer:
      'RP 依赖本地 League Client API 读取信息，不提供自动接受对局、自动 BP、自动换符文等代替玩家操作的功能，也不会在对局内提供不对称信息帮助。但第三方工具不存在绝对安全承诺，请你根据自己的判断使用。'
  },
  {
    question: 'RP 收费吗？',
    answer:
      'RP 是免费软件，并且在 GitHub 上公开项目页面。软件内的 AI 功能由用户自行配置 AI 厂商 API Key 实现，RP 本身不收取 AI 功能费用。'
  },
  {
    question: '免费软件怎么持续运维？',
    answer:
      'RP 由个人开发，没有广告，也不向用户收费。但开发、下载分发和后续维护仍然会产生成本。如果你觉得它有用，欢迎在 GitHub 上给项目点一颗 star；也可以通过软件内赞赏码支持开发，这会帮助 RP 持续迭代。'
  },
  {
    question: '我觉得 RP 没有 XXX 好用。',
    answer:
      '你完全可以选择更适合自己的工具。如果你愿意告诉我哪里不好用，这对 RP 很有价值。欢迎在 GitHub 提 issue，或在软件内留言反馈，我会尽我所能处理。'
  },
  {
    question: '怎么加入交流群？',
    answer: `如果你在使用中遇到问题、想反馈功能，或者想和其他用户交流，可以加入 QQ 群：${qqGroup}。也欢迎在 GitHub 提 issue。`,
    linkLabel: '加入 QQ 群',
    linkUrl: qqGroupUrl
  }
]

const storySectionRef = ref<HTMLElement | null>(null)
const activeStoryIndex = ref(0)
const storyProgress = ref(0)
const postgameMode = ref<'review' | 'praise'>('review')

let scrollFrameId: number | null = null
let revealObserver: IntersectionObserver | null = null

const activeStory = computed(() => storyScenes[activeStoryIndex.value] ?? storyScenes[0])
const storyRailStyle = computed(() => ({
  height: `${Math.round(storyProgress.value * 100)}%`
}))

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function updateStoryProgress() {
  const section = storySectionRef.value

  if (!section || window.matchMedia('(max-width: 840px)').matches) {
    activeStoryIndex.value = 0
    storyProgress.value = 0
    return
  }

  const bounds = section.getBoundingClientRect()
  const scrollableHeight = Math.max(1, section.offsetHeight - window.innerHeight)
  const progress = clamp(-bounds.top / scrollableHeight, 0, 1)

  storyProgress.value = progress
  activeStoryIndex.value = Math.min(storyScenes.length - 1, Math.floor(progress * storyScenes.length))
}

function requestStoryProgressUpdate() {
  if (scrollFrameId !== null) {
    return
  }

  scrollFrameId = window.requestAnimationFrame(() => {
    scrollFrameId = null
    updateStoryProgress()
  })
}

function scrollToStoryStep(index: number) {
  const section = storySectionRef.value

  if (!section) {
    return
  }

  const targetIndex = clamp(index, 0, storyScenes.length - 1)
  const scrollableHeight = Math.max(0, section.offsetHeight - window.innerHeight)
  const divisor = Math.max(1, storyScenes.length - 1)

  window.scrollTo({
    top: section.offsetTop + scrollableHeight * (targetIndex / divisor),
    behavior: 'smooth'
  })
}

function setPostgameMode(mode: 'review' | 'praise') {
  postgameMode.value = mode
}

function observeRevealSections() {
  const revealNodes = Array.from(document.querySelectorAll<HTMLElement>('.reveal-on-scroll'))

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) {
    revealNodes.forEach((node) => node.classList.add('is-visible'))
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver?.unobserve(entry.target)
        }
      })
    },
    {
      threshold: 0.16,
      rootMargin: '0px 0px -12% 0px'
    }
  )

  revealNodes.forEach((node) => revealObserver?.observe(node))
}

onMounted(() => {
  updateStoryProgress()
  observeRevealSections()
  window.addEventListener('scroll', requestStoryProgressUpdate, { passive: true })
  window.addEventListener('resize', requestStoryProgressUpdate)
})

onUnmounted(() => {
  window.removeEventListener('scroll', requestStoryProgressUpdate)
  window.removeEventListener('resize', requestStoryProgressUpdate)

  if (scrollFrameId !== null) {
    window.cancelAnimationFrame(scrollFrameId)
  }

  revealObserver?.disconnect()
  revealObserver = null
})
</script>

<template>
  <div class="site-shell">
    <header class="site-nav" aria-label="RankPeek 官网导航">
      <a class="brand-lockup" href="#top" aria-label="RankPeek 首页">
        <img :src="assets.logo" alt="" class="brand-logo" />
        <span>RankPeek</span>
      </a>
      <a class="nav-action" :href="githubUrl" target="_blank" rel="noreferrer">GitHub</a>
    </header>

    <main id="top">
      <section class="hero-section" aria-labelledby="hero-title">
        <div class="hero-visual" aria-hidden="true">
          <img :src="assets.heroGlow" alt="" />
        </div>

        <div class="hero-copy">
          <h1 id="hero-title">RankPeek</h1>
          <p class="hero-kicker">AI复盘、数据分析、性能强大的本地工具。免费开源，享受游戏。</p>

          <div class="hero-bottom">
            <div class="hero-actions" aria-label="主要操作">
              <a class="primary-action" :href="downloadUrl" target="_blank" rel="noreferrer">下载</a>
              <a class="secondary-action" :href="releaseUrl" target="_blank" rel="noreferrer">
                GitHub release
              </a>
            </div>
            <p class="hero-meta">V1.0.0 WIN10+</p>
          </div>
        </div>
      </section>

      <section
        id="features"
        ref="storySectionRef"
        class="story-section reveal-on-scroll"
        aria-label="RankPeek 功能展示"
      >
        <div class="story-sticky">
          <div class="story-shell">
            <aside class="story-nav" aria-label="功能展示进度">
              <div class="story-rail" aria-hidden="true">
                <span :style="storyRailStyle"></span>
              </div>

              <div class="story-step-list" role="tablist" aria-label="RankPeek 功能场景">
                <button
                  v-for="(scene, index) in storyScenes"
                  :key="scene.id"
                  :class="['story-step', { active: activeStoryIndex === index }]"
                  type="button"
                  role="tab"
                  :aria-selected="activeStoryIndex === index"
                  @click="scrollToStoryStep(index)"
                >
                  <span>{{ scene.step }}</span>
                  <strong>{{ scene.label }}</strong>
                </button>
              </div>
            </aside>

            <article :key="activeStory.id" class="story-content">
              <h3>{{ activeStory.title }}</h3>
              <p>{{ activeStory.body }}</p>

              <ul v-if="activeStory.points.length" class="story-point-list">
                <li v-for="point in activeStory.points" :key="point">{{ point }}</li>
              </ul>

              <small>{{ activeStory.note }}</small>
            </article>

            <div class="story-stage" aria-label="功能画面示意">
              <div v-if="activeStory.visual === 'pregame'" class="story-visual story-pregame">
                <img :src="assets.pregame" alt="" />
              </div>

              <div v-else-if="activeStory.visual === 'opgg'" class="story-visual story-opgg">
                <div class="opgg-showcase" aria-hidden="true">
                  <img class="opgg-shot opgg-shot-list" :src="assets.opggList" alt="" />
                  <img class="opgg-shot opgg-shot-detail" :src="assets.opggDetail" alt="" />
                  <div class="opgg-sync-badge">跟随当前对局</div>
                </div>
              </div>

              <div v-else-if="activeStory.visual === 'rp'" class="story-visual story-rp">
                <img :src="assets.rpIndex" alt="" />
              </div>

              <div v-else-if="activeStory.visual === 'postgameAi'" class="story-visual story-postgame">
                <div :class="['postgame-showcase', `is-${postgameMode}`]">
                  <button
                    :class="[
                      'postgame-shot postgame-shot-review',
                      { 'is-main': postgameMode === 'review', 'is-thumb': postgameMode === 'praise' }
                    ]"
                    type="button"
                    :tabindex="postgameMode === 'review' ? -1 : 0"
                    aria-label="切换到赛后复盘截图"
                    @click="setPostgameMode('review')"
                  >
                    <img :src="assets.postgameReview" alt="" />
                  </button>

                  <button
                    :class="[
                      'postgame-shot postgame-shot-praise',
                      { 'is-main': postgameMode === 'praise', 'is-thumb': postgameMode === 'review' }
                    ]"
                    type="button"
                    :tabindex="postgameMode === 'praise' ? -1 : 0"
                    aria-label="切换到夸夸机截图"
                    @click="setPostgameMode('praise')"
                  >
                    <img :src="assets.postgamePraise" alt="" />
                  </button>
                </div>
              </div>

              <div v-else class="story-visual story-coach">
                <img :src="assets.coachReport" alt="" />
              </div>
            </div>

            <div class="story-mobile-list" aria-label="RankPeek 功能场景列表">
              <article v-for="scene in storyScenes" :key="`mobile-${scene.id}`" class="story-mobile-card">
                <h3>{{ scene.title }}</h3>
                <p>{{ scene.body }}</p>

                <ul v-if="scene.points.length">
                  <li v-for="point in scene.points" :key="point">{{ point }}</li>
                </ul>

                <small>{{ scene.note }}</small>
              </article>
            </div>
          </div>
        </div>
      </section>

      <section class="feature-section section-block reveal-on-scroll" aria-labelledby="feature-title">
        <div class="section-heading">
          <h2 id="feature-title">AI 功能不收费，成本由你掌控。</h2>
          <p>
            RankPeek 免费开源，本地能力和 RP 指数不调用
            AI。赛前分析、赛后复盘、夸夸机和电子教练使用你自己的 API Key；以 DeepSeek
            实测成本估算，100 局排位顶配使用也约 2 元以内。
          </p>
        </div>

        <div class="feature-ai-cost" aria-label="AI 成本说明">
          <div class="cost-ticket-main">
            <span>月成本示例</span>
            <div class="cost-ticket-headline">
              <strong>100 局</strong>
              <strong>排位</strong>
              <strong class="cost-ticket-result">≈ 2 元以内</strong>
            </div>
            <p>按 DeepSeek 实测均价估算，实际通常更低。</p>
          </div>

          <div class="cost-ticket-detail">
            <div class="cost-formula" aria-label="AI 成本计算公式">
              <b>0.005 元 / 次</b>
              <i>×</i>
              <b>每局最多 4 次</b>
              <i>×</i>
              <b>100 局</b>
              <i>=</i>
              <strong>约 2 元以内</strong>
            </div>

            <dl class="cost-notes">
              <div>
                <dt>实测均价</dt>
                <dd>按 DeepSeek 价格和实际使用样本估算。</dd>
              </div>
              <div>
                <dt>顶配调用</dt>
                <dd>赛前 2 次 + 赛后 2 次，实际触发通常更少。</dd>
              </div>
              <div>
                <dt>费用结算</dt>
                <dd>RankPeek 不收取 AI 功能费，由你的 AI 厂商结算。</dd>
              </div>
            </dl>
          </div>
        </div>
      </section>

      <section class="faq-section section-block reveal-on-scroll" aria-labelledby="faq-title">
        <div class="section-heading">
          <p class="eyebrow">FAQ</p>
          <h2 id="faq-title">常见问题</h2>
        </div>

        <div class="faq-grid">
          <article v-for="item in faqItems" :key="item.question" class="faq-item">
            <h3>{{ item.question }}</h3>
            <p>{{ item.answer }}</p>
            <a
              v-if="item.linkUrl"
              class="faq-link"
              :href="item.linkUrl"
              target="_blank"
              rel="noreferrer"
            >
              {{ item.linkLabel }}
            </a>
          </article>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <span>RankPeek</span>
      <div class="footer-meta">
        <p>免费开源的 League of Legends 本地辅助分析工具。RankPeek 与 Riot Games / 腾讯游戏无官方关联。</p>
        <a :href="qqGroupUrl" target="_blank" rel="noreferrer">QQ群：{{ qqGroup }}</a>
      </div>
    </footer>
  </div>
</template>
