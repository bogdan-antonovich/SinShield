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
        dateModified: article.publishedAt,
        author: {
          '@type': article.author === 'SinShield Editorial' ? 'Organization' : 'Person',
          name: article.author,
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
          <p>${escapeHtml(article.author)} · <time datetime="${article.publishedAt}">${escapeHtml(article.publishedAt)}</time> · ${escapeHtml(article.readingTime)}</p>
          <h1>${escapeHtml(article.title)}</h1>
          <p>${escapeHtml(article.description)}</p>
          <img src="${escapeHtml(article.thumbnail)}" alt="${escapeHtml(article.thumbnailAlt)}">
        </header>
        ${sections}
        <p><a href="/blog">Back to the library</a></p>
      </article>
    </main>`
}

function blogBody() {
  const [featured, ...library] = articles

  return `
    <main>
      <section>
        <p>Latest article</p>
        <h1><a href="${articlePath(featured)}">${escapeHtml(featured.title)}</a></h1>
        <p>${escapeHtml(featured.author)} · <time datetime="${featured.publishedAt}">${escapeHtml(featured.publishedAt)}</time></p>
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

const GOOGLE_PLAY_URL = 'https://play.google.com/store/apps/details?id=com.example.sinshield'
const SUPPORT_EMAIL = 'support@sinshield.app'
const socialProfiles = [] // add real profile URLs here to populate Organization.sameAs

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

const marketingPages = [
  {
    title: 'SinShield — Private On-Device Adult Content Blocker',
    description:
      'SinShield blocks adult content on your phone and makes it harder to give in when temptation hits. Everything stays on your device, with no accounts or activity tracking.',
    pathName: '/',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [organizationSchema, websiteSchema],
    body: homeBody(),
  },
  {
    title: 'Get SinShield — Adult Content Blocker for Android',
    description:
      'Choose your platform and get SinShield, the private on-device adult content blocker. Available for Android with no account, no tracking, and no uploaded screenshots.',
    pathName: '/products',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [organizationSchema],
    body: productsBody(),
  },
  {
    title: 'SinShield for Android — Block Adult Content Privately',
    description:
      'SinShield blocks adult websites and covers explicit content inside Instagram and X, with every check kept on your phone. No accounts, no tracking, no judgment.',
    pathName: '/products/android',
    image: '/sinshield-thumbnail.jpg',
    structuredData: [softwareApplicationSchema],
    body: androidBody(),
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
  title: 'SinShield Blog — Healthier Digital Habits',
  description:
    'Practical, private, and judgment-free guidance for healthier digital habits, focus, and recovery.',
  pathName: '/blog',
  image: featuredArticle.thumbnail,
  body: blogBody(),
})

await mkdir(path.join(distDirectory, 'blog'), { recursive: true })
await writeFile(path.join(distDirectory, 'blog/index.html'), blogDocument)

for (const article of articles) {
  const directory = path.join(distDirectory, 'blog', article.slug)
  const document = createDocument({
    title: `${article.title} — SinShield`,
    description: article.description,
    pathName: articlePath(article),
    image: article.thumbnail,
    type: 'article',
    article,
    body: articleBody(article),
  })

  await mkdir(directory, { recursive: true })
  await writeFile(path.join(directory, 'index.html'), document)
}

const sitemapPaths = ['/', '/products', '/products/android', '/blog', ...articles.map(articlePath)]
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${sitemapPaths.map((pathName) => `  <url><loc>${escapeHtml(`${siteOrigin}${pathName}`)}</loc></url>`).join('\n')}
</urlset>
`

await writeFile(path.join(distDirectory, 'sitemap.xml'), sitemap)
await writeFile(
  path.join(distDirectory, 'robots.txt'),
  `User-agent: *\nAllow: /\n\nSitemap: ${siteOrigin}/sitemap.xml\n`,
)
