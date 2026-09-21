import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { parseArticleCollection } from '../src/modules/blog/markdown.mjs'

const projectRoot = process.cwd()
const distDirectory = path.join(projectRoot, 'dist')
const siteOrigin = (process.env.VITE_SITE_URL || 'https://sinshield.app').replace(/\/$/, '')
const contentDirectory = path.join(projectRoot, 'src/modules/blog/content')
const articleFileNames = (await readdir(contentDirectory)).filter((fileName) => fileName.endsWith('.md'))
const articles = parseArticleCollection(
  Object.fromEntries(
    await Promise.all(
      articleFileNames.map(async (fileName) => [
        fileName,
        await readFile(path.join(contentDirectory, fileName), 'utf8'),
      ]),
    ),
  ),
)
const baseDocument = await readFile(path.join(distDirectory, 'index.html'), 'utf8')

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function absoluteUrl(value) {
  return value.startsWith('http') ? value : `${siteOrigin}${value.startsWith('/') ? '' : '/'}${value}`
}

function withoutPageMetadata(document) {
  return document
    .replace(/\s*<meta\s+(?:name|property)="(?:description|og:[^"]+|twitter:[^"]+|article:[^"]+)"[^>]*>/gi, '')
    .replace(/\s*<link\s+rel="canonical"[^>]*>/gi, '')
    .replace(/\s*<script\s+type="application\/ld\+json"[^>]*>[\s\S]*?<\/script>/gi, '')
}

function jsonLdScript(data) {
  return `<script type="application/ld+json">${JSON.stringify(data).replaceAll('<', '\\u003c')}</script>`
}

function breadcrumbSchema(trail) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: trail.map((crumb, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: crumb.label,
      item: absoluteUrl(crumb.href),
    })),
  }
}

function faqSchema(items) {
  return {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: items.map((item) => ({
      '@type': 'Question',
      name: item.question,
      acceptedAnswer: { '@type': 'Answer', text: item.answer },
    })),
  }
}

function howToSchema({ name, description, url, steps }) {
  return {
    '@context': 'https://schema.org',
    '@type': 'HowTo',
    name,
    ...(description ? { description } : {}),
    step: steps.map((step, index) => ({
      '@type': 'HowToStep',
      position: index + 1,
      name: step.name,
      ...(step.text ? { text: step.text } : {}),
      ...(url ? { url: `${url}#step-${index + 1}` } : {}),
    })),
  }
}

function metadataMarkup({ title, description, pathName, image, type = 'website', article, structuredData = [] }) {
  const canonicalUrl = `${siteOrigin}${pathName}`
  const absoluteImage = image ? absoluteUrl(image) : undefined
  const articleSchema = article
    ? {
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: article.title,
        description: article.description,
        image: absoluteImage,
        datePublished: article.publishedAt,
        dateModified: article.updatedAt ?? article.publishedAt,
        author: {
          '@type': article.author === 'SinShield Editorial' ? 'Organization' : 'Person',
          name: article.author,
          ...(article.author === 'SinShield Editorial'
            ? { url: `${siteOrigin}/authors/sinshield-editorial` }
            : {}),
        },
        publisher: {
          '@type': 'Organization',
          name: 'SinShield',
          logo: {
            '@type': 'ImageObject',
            url: `${siteOrigin}/sinshield-thumbnail.png`,
          },
        },
        mainEntityOfPage: canonicalUrl,
      }
    : undefined

  const allStructuredData = [articleSchema, ...structuredData].filter(Boolean)

  return [
    `<meta name="description" content="${escapeHtml(description)}">`,
    `<link rel="canonical" href="${escapeHtml(canonicalUrl)}">`,
    `<meta property="og:title" content="${escapeHtml(title)}">`,
    `<meta property="og:description" content="${escapeHtml(description)}">`,
    `<meta property="og:type" content="${type}">`,
    `<meta property="og:url" content="${escapeHtml(canonicalUrl)}">`,
    absoluteImage ? `<meta property="og:image" content="${escapeHtml(absoluteImage)}">` : '',
    article ? `<meta property="article:published_time" content="${article.publishedAt}">` : '',
    article?.updatedAt ? `<meta property="article:modified_time" content="${article.updatedAt}">` : '',
    article ? `<meta property="article:author" content="${escapeHtml(article.author)}">` : '',
    `<meta name="twitter:card" content="${absoluteImage ? 'summary_large_image' : 'summary'}">`,
    `<meta name="twitter:title" content="${escapeHtml(title)}">`,
    `<meta name="twitter:description" content="${escapeHtml(description)}">`,
    absoluteImage ? `<meta name="twitter:image" content="${escapeHtml(absoluteImage)}">` : '',
    ...allStructuredData.map(jsonLdScript),
  ]
    .filter(Boolean)
    .join('\n    ')
}

function createDocument({ title, description, pathName, image, type, article, structuredData, body }) {
  return withoutPageMetadata(baseDocument)
    .replace(/<title>[\s\S]*?<\/title>/i, `<title>${escapeHtml(title)}</title>`)
    .replace(
      '</head>',
      `    ${metadataMarkup({ title, description, pathName, image, type, article, structuredData })}\n  </head>`,
    )
    .replace('<div id="app"></div>', `<div id="app">${body}</div>`)
}

function articlePath(article) {
  return `/blog/${article.slug}`
}

const articleMetaTitles = {
  'benefits-of-quitting-porn': 'Benefits of Quitting Porn: 8 Realistic Changes',
  'best-accountability-apps-to-quit-porn': 'Best Accountability Apps to Quit Porn (2026)',
  'countries-where-porn-is-illegal': 'Where Is Porn Illegal? Country Laws Explained',
  'does-porn-lower-your-iq': 'Does Porn Lower Your IQ? Research Explained',
  'how-to-break-the-scroll-trigger-loop': 'How to Break the Scroll–Trigger Loop',
  'porn-on-x': 'Porn on X: Rules, Filters, and How to Block It',
}

function articleMetaTitle(article) {
  return articleMetaTitles[article.slug] ?? article.title
}

function relatedArticles(article) {
  return articles
    .filter((candidate) => candidate.slug !== article.slug)
    .map((candidate) => ({
      article: candidate,
      sharedTags: candidate.tags.filter((tag) => article.tags.includes(tag)).length,
    }))
    .sort((left, right) => right.sharedTags - left.sharedTags || Date.parse(right.article.publishedAt) - Date.parse(left.article.publishedAt))
    .slice(0, 3)
    .map(({ article: candidate }) => candidate)
}

function renderRichText(value) {
  if (typeof value === 'string') return escapeHtml(value)

  return value
    .map((run) => {
      if (typeof run === 'string') return escapeHtml(run)
      if (run.href) {
        const external = !run.href.startsWith('/')
        const attributes = external ? ' target="_blank" rel="noopener noreferrer"' : ''
        return `<a href="${escapeHtml(run.href)}"${attributes}>${escapeHtml(run.text)}</a>`
      }
      if (run.bold) return `<strong>${escapeHtml(run.text)}</strong>`
      return escapeHtml(run.text)
    })
    .join('')
}

function renderBlock(block) {
  switch (block.type) {
    case 'paragraph':
      return `<p>${renderRichText(block.text)}</p>`
    case 'heading':
      return `<h3>${renderRichText(block.text)}</h3>`
    case 'list': {
      const tag = block.ordered ? 'ol' : 'ul'
      const items = block.items.map((item) => `<li>${renderRichText(item)}</li>`).join('')
      return `<${tag}>${items}</${tag}>`
    }
    case 'table': {
      const head = block.columns.map((column) => `<th scope="col">${escapeHtml(column)}</th>`).join('')
      const body = block.rows
        .map((row) => `<tr>${row.map((cell) => `<td>${renderRichText(cell)}</td>`).join('')}</tr>`)
        .join('')
      return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
    }
    case 'callout':
      return `<aside>${
        block.label ? `<p><strong>${escapeHtml(block.label)}</strong></p>` : ''
      }<p>${renderRichText(block.text)}</p></aside>`
    default:
      return ''
  }
}

function renderSectionBody(section) {
  if (Array.isArray(section.blocks)) {
    return section.blocks.map(renderBlock).join('\n')
  }
  return (section.paragraphs ?? []).map((paragraph) => `<p>${escapeHtml(paragraph)}</p>`).join('\n')
}

function articleBody(article) {
  const sections = article.sections
    .map(
      (section) => `
        <section>
          ${section.heading ? `<h2>${escapeHtml(section.heading)}</h2>` : ''}
          ${renderSectionBody(section)}
        </section>`,
    )
    .join('\n')

  return `
    <main>
      <article>
        <nav aria-label="Breadcrumb"><a href="/">Home</a> / <a href="/blog">Blog</a></nav>
        <header>
          <p><a href="/authors/sinshield-editorial">${escapeHtml(article.author)}</a> · <time datetime="${article.publishedAt}">${escapeHtml(article.publishedAt)}</time>${article.updatedAt ? ` · updated <time datetime="${article.updatedAt}">${escapeHtml(article.updatedAt)}</time>` : ''} · ${escapeHtml(article.readingTime)}</p>
          <h1>${escapeHtml(article.title)}</h1>
          <p>${escapeHtml(article.description)}</p>
          <img src="${escapeHtml(article.thumbnail)}" alt="${escapeHtml(article.thumbnailAlt)}">
        </header>
        ${sections}
        <aside>
          <h2>About this article</h2>
          <p>Published by <a href="/authors/sinshield-editorial">SinShield Editorial</a> under our <a href="/editorial-standards">editorial standards</a>. Product ownership, limitations, source quality, and corrections are reviewed before publication.</p>
        </aside>
        <section>
          <h2>Related reading</h2>
          <ul>${relatedArticles(article).map((related) => `<li><a href="${articlePath(related)}">${escapeHtml(related.title)}</a></li>`).join('')}</ul>
        </section>
        <p><a href="/blog">Back to the library</a></p>
      </article>
    </main>`
}

function blogBody() {
  const [featured, ...library] = articles

  return `
    <main>
      <header>
        <h1>Everything You Need to Know About Porn and How to Block It</h1>
        <p>From porn on social media and adult-site laws to accountability apps and the effects of quitting, the SinShield blog gives you clear answers to the questions that matter when you are trying to stop watching porn.</p>
      </header>
      <section>
        <p>Featured article</p>
        <h2><a href="${articlePath(featured)}">${escapeHtml(featured.title)}</a></h2>
        <p><a href="/authors/sinshield-editorial">${escapeHtml(featured.author)}</a> · <time datetime="${featured.publishedAt}">${escapeHtml(featured.publishedAt)}</time></p>
        <a href="${articlePath(featured)}">Read more</a>
        <a href="${articlePath(featured)}"><img src="${escapeHtml(featured.thumbnail)}" alt="${escapeHtml(featured.thumbnailAlt)}"></a>
      </section>
      <section>
        <h2>The library</h2>
        ${library
          .map(
            (article) => `
          <article>
            <a href="${articlePath(article)}"><img src="${escapeHtml(article.thumbnail)}" alt="${escapeHtml(article.thumbnailAlt)}"></a>
            <h3><a href="${articlePath(article)}">${escapeHtml(article.title)}</a></h3>
            <p>${escapeHtml(article.author)} · <time datetime="${article.publishedAt}">${escapeHtml(article.publishedAt)}</time></p>
          </article>`,
          )
          .join('\n')}
      </section>
    </main>`
}

// ---- Marketing page prerendering (home, products, android) ----
// The copy below mirrors the Vue section components so non-JS crawlers and AI
// engines receive the same content the client renders after hydration.

const GOOGLE_PLAY_URL = 'https://play.google.com/store/apps/details?id=app.sinshield'
const SUPPORT_EMAIL = 'support@sinshield.app'
const socialProfiles = [] // add real profile URLs here to populate Organization.sameAs

// Mirrors src/modules/main/components/FaqSection.vue so crawlers and AI engines
// receive the same FAQ content the homepage renders after hydration, and so the
// FAQPage schema below reflects visible on-page text.
const homeFaqItems = [
  {
    question: 'What does SinShield block?',
    answer:
      'SinShield covers explicit and suggestive content inside supported apps, including Instagram and X, and blocks known adult websites through a separate website-protection layer. You can also add domains to your personal block list.',
  },
  {
    question: 'Are my screenshots or browsing activity uploaded?',
    answer:
      'No. Screen captures are analyzed by models running on your Android device and are not sent to SinShield. Website protection checks standard DNS requests locally; SinShield does not receive your browsing history or inspect the contents of the pages you visit.',
  },
  {
    question: 'Why does SinShield need Accessibility and overlay permissions?',
    answer:
      'Android requires Accessibility permission so SinShield can notice changes in supported apps and capture the visible screen for on-device analysis. The overlay permission lets it place a cover over unsafe content. You grant and can revoke both permissions in Android settings.',
  },
  {
    question: 'Is website protection a regular VPN?',
    answer:
      'No. SinShield uses Android’s local VPN feature only to filter standard DNS requests for known adult domains. It does not route, decrypt, or inspect your web traffic. Because Android allows one VPN at a time, website protection cannot run alongside another VPN.',
  },
  {
    question: 'Can SinShield make a mistake?',
    answer:
      'Like any automated detection, it can occasionally cover safe content or miss something unsafe. You can report a false positive from the block screen, and SinShield remembers that correction locally so the same frame is not covered again.',
  },
  {
    question: 'Can I choose how strict the protection is?',
    answer:
      'Yes. SinShield includes five protection levels, from Relaxed to Maximum, a separate option for suggestive content, and advanced detection controls. The recommended settings are a good place to start.',
  },
  {
    question: 'What happens when content is blocked?',
    answer:
      'SinShield creates a short pause and covers the unsafe content. You can then move past it, return to your feed, or close the app. The shield is there to interrupt the impulse, while the final choice stays with you.',
  },
  {
    question: 'Do I need an account or subscription?',
    answer:
      'No. SinShield is free to use and does not require an account or cloud profile. It is currently available for Android 11 and newer.',
  },
]

// Mirrors the numbered steps in src/modules/products/components/StepsSection.vue.
const androidInstallSteps = [
  { name: 'Open Google Play', text: 'Tap the Get it on Google Play button to open the SinShield listing.' },
  { name: 'Install the Android app', text: 'Install SinShield from Google Play on your Android device.' },
  { name: 'Stay protected', text: 'Grant the requested permissions once and SinShield keeps working in the background.' },
]

function paragraphs(items) {
  return items.map((text) => `<p>${escapeHtml(text)}</p>`).join('\n')
}

function breadcrumbNav(trail) {
  return `<nav aria-label="Breadcrumb">${trail
    .map((crumb) => (crumb.href ? `<a href="${escapeHtml(crumb.href)}">${escapeHtml(crumb.label)}</a>` : escapeHtml(crumb.label)))
    .join(' / ')}</nav>`
}

function homeBody() {
  return `
    <main>
      <section>
        <h1>SinShield Is a Data Safe Free Adult Content Blocker Directly On Your Device</h1>
        <p>Porn can quietly take over your time, drain your focus, and leave you feeling like you’re no longer in control, which is why SinShield blocks adult content on your phone and makes it harder to give in when temptation hits, giving you the support you need to break the habit and become the man you want to be without judgment or empty promises.</p>
        <p><a href="/products/android">SinShield for Android</a></p>
      </section>
      <section>
        <h2>Protection You Can Rely On</h2>
        ${paragraphs([
          'Leaving porn behind is not a question of understanding the damage, because most men here already know exactly what it costs them. The value of this app is practical: it makes access less automatic, giving a decision made with a clear head more weight than an impulse that lasts a few minutes.',
          'The app keeps porn and other sexual content off your screen, while everything happens on your phone, so your activity is not sent anywhere. It runs in the background and steps in when adult content appears.',
        ])}
        <p><a href="/products">Get Started</a></p>
      </section>
      <section>
        <h2>How SinShield Works</h2>
        <p>Inside protected apps, SinShield responds to screen changes, captures the current frame, analyzes it with on-device models, and covers content that receives an unsafe verdict. Website protection checks DNS requests against an adult-domain list and rejects matches before the site loads.</p>
        <ol>
          <li><strong>A screen change starts a scan.</strong> When a protected app reports a window or content change, SinShield schedules a fresh capture. Rapid changes are combined so the scanner analyzes the newest visible frame instead of queuing stale frames.</li>
          <li><strong>The visible frame is analyzed.</strong> The captured frame is classified on the device. SinShield scores both the full screen and detected media regions for explicit and suggestive content; unchanged known-safe frames are skipped.</li>
          <li><strong>Uncertain detections are checked again.</strong> A clear unsafe score produces a final verdict. Borderline detections receive a provisional cover and a quick follow-up scan; an independent verifier is used when the policy requires another signal.</li>
          <li><strong>Unsafe content is covered.</strong> A final unsafe verdict places a full-screen shield in supported apps or a cover over the detected media region. Separately, the local DNS filter rejects requests for known adult domains before those sites can load.</li>
        </ol>
      </section>
      <section>
        <h2>Control Still Belongs to You</h2>
        ${paragraphs([
          'SinShield does not make the decision for you. It creates a brief pause between seeing sexual content and acting on the impulse, giving you time to breathe and choose what happens next.',
          'When the shield appears, you can scroll past, close the app, or return to your feed. The choice remains yours; SinShield simply makes it easier to follow the decision you made with a clear head.',
        ])}
      </section>
      <section>
        <h2>Where SinShield Protects You</h2>
        <ul>
          <li>X</li>
          <li>Instagram</li>
          <li>Adult Websites</li>
        </ul>
      </section>
      <section>
        <h2>You Decide How Strict Protection Should Be</h2>
        ${paragraphs([
          'SinShield works well with its recommended settings, but its detection sensitivity controls let you decide how cautious protection should be by adjusting when explicit or suggestive content is covered and when an uncertain result should receive a second check before the app decides what to do.',
          'Lower settings help SinShield catch more questionable content, while higher settings reduce the chance of ordinary posts being covered, which means you can choose stronger protection or a lighter touch and change that balance whenever your needs change.',
        ])}
        <p><a href="/products">Get Started</a></p>
      </section>
      <section id="faq">
        <h2>FAQ</h2>
        ${homeFaqItems
          .map(
            (item) => `
        <details>
          <summary>${escapeHtml(item.question)}</summary>
          <p>${escapeHtml(item.answer)}</p>
        </details>`,
          )
          .join('\n')}
      </section>
    </main>`
}

function productsBody() {
  return `
    <main>
      <article>
        ${breadcrumbNav([{ label: 'Home', href: '/' }, { label: 'Products' }])}
        <section>
          <h1>Where Would You Like to Start?</h1>
          <p><a href="/products/android">SinShield for Android</a></p>
        </section>
        <section>
          <h2>SinShield for Android</h2>
          <p><a href="${GOOGLE_PLAY_URL}" target="_blank" rel="noopener noreferrer">Get it on Google Play</a></p>
          <ol>
            <li>Click the above download button</li>
            <li>Install the Android app</li>
            <li>Stay protected</li>
          </ol>
        </section>
        <section>
          <h2>Is SinShield the right protection for you?</h2>
          <p>SinShield is built for Android users who want private, automatic friction between an unexpected trigger and the next action. It analyzes visible content in supported apps on the device and separately blocks requests to known adult websites, so the two protection layers cover different routes without creating a cloud activity profile.</p>
          <h3>A good fit when</h3>
          <ul>
            <li>You use Android 11 or newer.</li>
            <li>You want image analysis to stay on your phone.</li>
            <li>You need protection in Instagram, X, and the browser.</li>
            <li>You prefer no account and no partner reports.</li>
          </ul>
          <h3>Know the limits</h3>
          <ul>
            <li>SinShield is not currently available for iPhone.</li>
            <li>Automatic detection can miss content or cover a safe image.</li>
            <li>It does not send accountability reports to another person.</li>
            <li>Android permits only one local VPN at a time.</li>
          </ul>
          <p>Read how <a href="/products/android">SinShield for Android works</a>, or review our <a href="/privacy-policy">privacy policy</a> before installing.</p>
        </section>
      </article>
    </main>`
}

const androidFeatures = [
  ['On-device privacy', '0 screenshots uploaded', 'Visible frames are analyzed and discarded on your phone.'],
  ['Supported apps', 'Instagram + X', 'Focused protection for the apps SinShield supports today.'],
  ['Protection layers', '2 layers', 'Screen shielding and local DNS filtering work independently.'],
  ['Detection', '2-model checks', 'Borderline results can receive an independent second opinion.'],
  ['Protection levels', '5 levels', 'Choose from Relaxed through Maximum sensitivity.'],
  ['Account required', 'None', 'Start protecting your device without creating a cloud profile.'],
  ['Website control', 'Built-in + custom', 'Block known adult sites and add domains of your own.'],
]

function androidBody() {
  return `
    <main>
      <article>
        ${breadcrumbNav([{ label: 'Home', href: '/' }, { label: 'Products', href: '/products' }, { label: 'Android' }])}
        <section>
          <h1>Private Protection for Your Android Device</h1>
          <p>SinShield blocks adult websites and covers explicit content inside supported apps, while every check stays on your phone. It gives you a practical layer of protection without accounts, activity tracking, or judgment.</p>
          <p><a href="${GOOGLE_PLAY_URL}" target="_blank" rel="noopener noreferrer">Get it on Google Play</a></p>
        </section>
        <section>
          <h2>Protection Without Giving Up Your Privacy</h2>
          <p>SinShield protects Instagram and X, blocks known adult websites, and keeps every image check on your phone. It gives you practical control over what reaches your screen without accounts, activity tracking, or uploaded screenshots.</p>
          <ul>
            ${androidFeatures.map(([label, value, detail]) => `<li><strong>${escapeHtml(label)}: ${escapeHtml(value)}.</strong> ${escapeHtml(detail)}</li>`).join('\n            ')}
          </ul>
        </section>
        <section>
          <h2>What should you know before using SinShield?</h2>
          <h3>Does SinShield upload screenshots?</h3>
          <p>No. Visible frames are analyzed by models running on your Android device and discarded there; SinShield does not receive those frames or use them to build an activity history.</p>
          <h3>Which apps does it protect?</h3>
          <p>Screen-level covering currently focuses on Instagram and X. A separate local DNS filter can block known adult domains opened from a browser or another app, and you can add domains to a personal block list.</p>
          <h3>Can it guarantee that nothing gets through?</h3>
          <p>No automated blocker can make that guarantee. SinShield adds friction and reduces easy access, but detection can occasionally miss unsafe content or cover a safe image, so it works best as one practical layer in a broader plan.</p>
          <p>See the <a href="/#faq">full FAQ</a> or browse the <a href="/blog">SinShield guides</a>.</p>
        </section>
      </article>
    </main>`
}

function aboutBody() {
  return `
    <main>
      <article>
        ${breadcrumbNav([{ label: 'Home', href: '/' }, { label: 'About' }])}
        <header>
          <h1>A private layer of friction between a trigger and your next choice</h1>
          <p>SinShield is an Android adult-content blocker built around a simple idea: a decision made with a clear head should have more support when an unexpected image, familiar feed, or known website appears later.</p>
        </header>
        <section>
          <h2>Private protection by design</h2>
          <ul>
            <li><strong>On-device analysis.</strong> Visible frames in supported apps are analyzed and discarded on the phone instead of being uploaded to SinShield.</li>
            <li><strong>Two protection layers.</strong> Screen-level covering protects supported feeds, while local DNS filtering blocks requests to known adult domains.</li>
            <li><strong>No account.</strong> You can use SinShield without creating a cloud profile, and it does not provide remote activity reports to another person.</li>
          </ul>
        </section>
        <section>
          <h2>Why we built it</h2>
          <p>People usually do not need another lecture about a habit they already want to change. They need practical support at the point where a routine becomes automatic, without surrendering a sensitive browsing history or a stream of screenshots to a remote service.</p>
          <p>SinShield focuses on interruption rather than surveillance. It can cover an unsafe result, reject a known adult-domain request, and create a short pause, but the person using the phone still decides what happens next.</p>
        </section>
        <section>
          <h2>What SinShield does not promise</h2>
          <p>SinShield is not therapy, a medical treatment, or a guarantee that every unsafe image or website will be blocked. Automated detection can make mistakes, Android permissions can be revoked, and determined users can change device settings. The app is a practical protection layer, not a claim that technology can make a difficult choice disappear.</p>
        </section>
        <section>
          <h2>How to reach us</h2>
          <p>Questions, corrections, privacy concerns, and product feedback are welcome at <a href="mailto:${SUPPORT_EMAIL}">${SUPPORT_EMAIL}</a>. You can also read our <a href="/editorial-standards">editorial standards</a>.</p>
        </section>
      </article>
    </main>`
}

function editorialStandardsBody() {
  return `
    <main>
      <article>
        ${breadcrumbNav([{ label: 'Home', href: '/' }, { label: 'Editorial standards' }])}
        <header>
          <h1>SinShield editorial standards</h1>
          <p>Our articles are written to help readers make practical decisions without shame, inflated claims, or disguised advertising. This page explains the standard we apply before publication and after an article goes live.</p>
        </header>
        <section>
          <h2>Evidence and current sources</h2>
          <p>Research claims should link to primary studies, systematic reviews, or official guidance whenever those sources are available. We distinguish correlation from causation and state when evidence is mixed or limited.</p>
          <p>Platform policies, prices, settings, product availability, and laws can change. We prefer official documentation, date time-sensitive claims, and encourage professional advice where an error could have legal or health consequences.</p>
        </section>
        <section>
          <h2>Disclosures and reader agency</h2>
          <p>SinShield articles may recommend SinShield, which is our product. Comparisons must disclose that relationship, give competing tools credit for jobs they perform better, and describe meaningful SinShield limitations.</p>
          <p>We do not diagnose readers, promise a cure, moralize about an unwanted exposure, or claim that software makes relapse impossible. Technology can support a decision; it cannot make that decision on someone’s behalf.</p>
        </section>
        <section>
          <h2>Our publication process</h2>
          <ol>
            <li>Define the reader’s specific question and practical decision.</li>
            <li>Gather official documentation, primary research, and authoritative guidance.</li>
            <li>Check material claims, limitations, uncertainty, and commercial disclosures.</li>
            <li>Review for clarity, harm, hidden bias, and unsupported certainty.</li>
            <li>Update articles when research, laws, policies, or product capabilities change.</li>
          </ol>
        </section>
        <section>
          <h2>Corrections</h2>
          <p>Email <a href="mailto:${SUPPORT_EMAIL}">${SUPPORT_EMAIL}</a> with the article URL and the claim in question. We review correction requests against the best available source and update material errors.</p>
          <p>Articles are published by <a href="/authors/sinshield-editorial">SinShield Editorial</a>, the product editorial function responsible for research, source checking, disclosures, and corrections.</p>
        </section>
      </article>
    </main>`
}

function editorialAuthorBody() {
  return `
    <main>
      <article>
        ${breadcrumbNav([{ label: 'Home', href: '/' }, { label: 'Blog', href: '/blog' }, { label: 'SinShield Editorial' }])}
        <header>
          <h1>SinShield Editorial</h1>
          <p>SinShield Editorial is the product editorial function behind the SinShield blog. It combines product knowledge with source-led research to publish practical guidance about adult-content protection, platform controls, digital habits, and the limits of blocking technology.</p>
        </header>
        <section>
          <h2>What this byline means</h2>
          <p>An organizational byline is used because these articles are produced and maintained as part of the SinShield product publication rather than presented as the independent opinion of a licensed clinician or lawyer. It also makes the commercial relationship clear: SinShield is our product.</p>
        </section>
        <section>
          <h2>How articles are reviewed</h2>
          <ul>
            <li>Product and privacy claims are checked against how the current Android app works.</li>
            <li>Research claims are traced to the cited paper, review, or authoritative guidance.</li>
            <li>Platform rules, prices, laws, and settings are checked against current official sources.</li>
            <li>Comparisons disclose SinShield ownership and describe alternatives and limitations.</li>
            <li>Corrections follow our published <a href="/editorial-standards">editorial standards</a>.</li>
          </ul>
        </section>
        <section>
          <h2>Contact the editorial team</h2>
          <p>Send source suggestions, corrections, or questions to <a href="mailto:${SUPPORT_EMAIL}">${SUPPORT_EMAIL}</a>, or browse all <a href="/blog">SinShield guides</a>.</p>
        </section>
      </article>
    </main>`
}

const organizationSchema = {
  '@context': 'https://schema.org',
  '@type': 'Organization',
  name: 'SinShield',
  url: `${siteOrigin}/`,
  logo: { '@type': 'ImageObject', url: `${siteOrigin}/sinshield-thumbnail.png` },
  description:
    'SinShield is a free adult content blocker that works directly on your device, keeping porn and other sexual content off your screen without sending your activity anywhere.',
  contactPoint: {
    '@type': 'ContactPoint',
    email: SUPPORT_EMAIL,
    contactType: 'customer support',
  },
  ...(socialProfiles.length ? { sameAs: socialProfiles } : {}),
}

const websiteSchema = {
  '@context': 'https://schema.org',
  '@type': 'WebSite',
  name: 'SinShield',
  url: `${siteOrigin}/`,
}

const softwareApplicationSchema = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'SinShield for Android',
  operatingSystem: 'Android',
  applicationCategory: 'SecurityApplication',
  description:
    'SinShield blocks adult websites and covers explicit content inside Instagram and X, with every check kept on your phone. No accounts, no activity tracking, no uploaded screenshots.',
  url: `${siteOrigin}/products/android`,
  downloadUrl: GOOGLE_PLAY_URL,
  installUrl: GOOGLE_PLAY_URL,
  image: `${siteOrigin}/sinshield-thumbnail.png`,
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
  publisher: { '@type': 'Organization', name: 'SinShield', url: `${siteOrigin}/` },
}

const editorialOrganizationSchema = {
  '@context': 'https://schema.org',
  '@type': 'Organization',
  name: 'SinShield Editorial',
  url: `${siteOrigin}/authors/sinshield-editorial`,
  parentOrganization: { '@type': 'Organization', name: 'SinShield', url: `${siteOrigin}/` },
  email: SUPPORT_EMAIL,
}

const marketingPages = [
  {
    title: 'Private Adult Content Blocker | SinShield',
    description:
      'SinShield privately covers explicit imagery and blocks known adult sites on Android. On-device protection, no account, and no uploaded screenshots.',
    pathName: '/',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [organizationSchema, websiteSchema, faqSchema(homeFaqItems)],
    body: homeBody(),
  },
  {
    title: 'Adult Content Blocker for Android | SinShield',
    description:
      'Get SinShield for Android: private screen-level adult-content covering and website blocking with no account or uploaded screenshots.',
    pathName: '/products',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [
      organizationSchema,
      breadcrumbSchema([
        { label: 'Home', href: '/' },
        { label: 'Products', href: '/products' },
      ]),
      howToSchema({
        name: 'How to install SinShield on Android',
        description: 'Get SinShield running on your Android device in three steps.',
        url: `${siteOrigin}/products`,
        steps: androidInstallSteps,
      }),
    ],
    body: productsBody(),
  },
  {
    title: 'Private Adult Content Blocker for Android | SinShield',
    description:
      'SinShield privately covers explicit content in supported Android apps and blocks known adult websites, with no account or uploaded screenshots.',
    pathName: '/products/android',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [
      softwareApplicationSchema,
      faqSchema([
        {
          question: 'Does SinShield upload screenshots?',
          answer: 'No. Visible frames are analyzed by models running on your Android device and discarded there; SinShield does not receive those frames or use them to build an activity history.',
        },
        {
          question: 'Which apps does SinShield protect?',
          answer: 'Screen-level covering currently focuses on Instagram and X. A separate local DNS filter can block known adult domains opened from a browser or another app.',
        },
        {
          question: 'Can SinShield guarantee that nothing gets through?',
          answer: 'No automated blocker can make that guarantee. SinShield adds friction and reduces easy access, but detection can occasionally miss unsafe content or cover a safe image.',
        },
      ]),
      breadcrumbSchema([
        { label: 'Home', href: '/' },
        { label: 'Products', href: '/products' },
        { label: 'Android', href: '/products/android' },
      ]),
    ],
    body: androidBody(),
  },
  {
    title: 'About SinShield | Private Android Protection',
    description:
      'Learn why SinShield exists, how its private Android protection works, what it can and cannot do, and how to contact the team.',
    pathName: '/about',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [
      organizationSchema,
      {
        '@context': 'https://schema.org',
        '@type': 'AboutPage',
        name: 'About SinShield',
        url: `${siteOrigin}/about`,
        mainEntity: { '@type': 'Organization', name: 'SinShield', url: `${siteOrigin}/` },
      },
      breadcrumbSchema([
        { label: 'Home', href: '/' },
        { label: 'About', href: '/about' },
      ]),
    ],
    body: aboutBody(),
  },
  {
    title: 'Editorial Standards and Corrections | SinShield',
    description:
      'How SinShield researches, reviews, updates, discloses conflicts, and corrects articles about digital habits and adult-content protection.',
    pathName: '/editorial-standards',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: 'SinShield editorial standards',
        url: `${siteOrigin}/editorial-standards`,
        author: { '@type': 'Organization', name: 'SinShield Editorial', url: `${siteOrigin}/authors/sinshield-editorial` },
      },
      breadcrumbSchema([
        { label: 'Home', href: '/' },
        { label: 'Editorial standards', href: '/editorial-standards' },
      ]),
    ],
    body: editorialStandardsBody(),
  },
  {
    title: 'SinShield Editorial | Author and Review Profile',
    description:
      'Meet the SinShield editorial function responsible for researching, reviewing, disclosing, updating, and correcting the SinShield blog.',
    pathName: '/authors/sinshield-editorial',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [
      editorialOrganizationSchema,
      {
        '@context': 'https://schema.org',
        '@type': 'ProfilePage',
        name: 'SinShield Editorial',
        url: `${siteOrigin}/authors/sinshield-editorial`,
        mainEntity: { '@type': 'Organization', name: 'SinShield Editorial', url: `${siteOrigin}/authors/sinshield-editorial` },
      },
      breadcrumbSchema([
        { label: 'Home', href: '/' },
        { label: 'Blog', href: '/blog' },
        { label: 'SinShield Editorial', href: '/authors/sinshield-editorial' },
      ]),
    ],
    body: editorialAuthorBody(),
  },
]

for (const page of marketingPages) {
  const document = createDocument({ ...page, type: 'website' })
  const directory = path.join(distDirectory, ...page.pathName.split('/').filter(Boolean))
  await mkdir(directory, { recursive: true })
  await writeFile(path.join(directory, 'index.html'), document)
}

const featuredArticle = articles[0]
const blogDocument = createDocument({
  title: 'Blog | SinShield',
  description:
    'Evidence-aware, judgment-free guides to adult-content blocking, digital habits, focus, and recovery from the SinShield editorial team.',
  pathName: '/blog',
  image: featuredArticle.thumbnail,
  structuredData: [
    {
      '@context': 'https://schema.org',
      '@type': ['Blog', 'CollectionPage'],
      name: 'SinShield guides',
      description: 'Evidence-aware, judgment-free guidance about adult-content blocking, digital habits, focus, and recovery.',
      url: `${siteOrigin}/blog`,
      publisher: { '@type': 'Organization', name: 'SinShield', url: `${siteOrigin}/` },
      mainEntity: {
        '@type': 'ItemList',
        itemListElement: articles.map((article, index) => ({
          '@type': 'ListItem',
          position: index + 1,
          url: `${siteOrigin}${articlePath(article)}`,
          name: article.title,
        })),
      },
    },
    breadcrumbSchema([
      { label: 'Home', href: '/' },
      { label: 'Blog', href: '/blog' },
    ]),
  ],
  body: blogBody(),
})

await mkdir(path.join(distDirectory, 'blog'), { recursive: true })
await writeFile(path.join(distDirectory, 'blog/index.html'), blogDocument)

for (const article of articles) {
  const directory = path.join(distDirectory, 'blog', article.slug)
  const document = createDocument({
    title: `${articleMetaTitle(article)} | SinShield`,
    description: article.description,
    pathName: articlePath(article),
    image: article.thumbnail,
    type: 'article',
    article,
    structuredData: [
      breadcrumbSchema([
        { label: 'Home', href: '/' },
        { label: 'Blog', href: '/blog' },
        { label: article.title, href: articlePath(article) },
      ]),
    ],
    body: articleBody(article),
  })

  await mkdir(directory, { recursive: true })
  await writeFile(path.join(directory, 'index.html'), document)
}

const pageLastModified = '2026-09-18'
const sitemapEntries = [
  ...marketingPages.map((page) => ({ pathName: page.pathName, lastmod: pageLastModified })),
  { pathName: '/blog', lastmod: articles.reduce((latest, article) => article.publishedAt > latest ? article.publishedAt : latest, '') },
  ...articles.map((article) => ({ pathName: articlePath(article), lastmod: article.updatedAt ?? article.publishedAt })),
]
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${sitemapEntries.map(({ pathName, lastmod }) => `  <url><loc>${escapeHtml(`${siteOrigin}${pathName}`)}</loc><lastmod>${lastmod}</lastmod></url>`).join('\n')}
</urlset>
`

await writeFile(path.join(distDirectory, 'sitemap.xml'), sitemap)
await writeFile(
  path.join(distDirectory, 'robots.txt'),
  `User-agent: *\nAllow: /\n\nSitemap: ${siteOrigin}/sitemap.xml\n`,
)
