# Writing and publishing a SinShield article

Articles are ordinary Markdown files in `src/modules/blog/content`. The website reads every `.md` file in that directory automatically, so there is no JSON to edit and no index to update.

## Create an article

Copy this template to `src/modules/blog/content/your-article-slug.md`:

```md
---
slug: your-article-slug
title: "Your article title"
description: "A short search-result and article-page description."
author: "SinShield Editorial"
publishedAt: 2026-09-15
readingTime: "6 min read"
thumbnail: "/images/your-article-header.webp"
thumbnailAlt: "A useful, specific description of the header image"
tags: ["Digital habits", "Focus"]
---

Write the introduction here. Normal Markdown paragraphs, **bold text**, and [links](https://example.com) are supported.

> [!NOTE] The short answer
> Use a callout for an important summary or practical takeaway.

## First main section

Continue the article here.

### A subsection

- Unordered list item
- Another item

1. Ordered step
2. Another step

| Option | Best for |
| --- | --- |
| Example | A short comparison |
```

Use `##` for the main sections shown in the table of contents and `###` for subsections. Place the thumbnail file in `public/images`. The `slug` must be unique and should match the Markdown filename.

To pin one article at the top of the blog, add `featured: true` to its front matter. Only one article should be featured at a time. Without that field, articles are ordered newest first by `publishedAt`.

## Preview and publish

From the `web` directory, run:

```sh
npm run dev
```

Open the local URL printed by Vite and review both `/blog` and `/blog/your-article-slug`.

Before publishing, verify the production build:

```sh
npm run build
```

Commit and push the new Markdown file and its image through the normal website deployment workflow. The build includes the article in the blog library, its own page, prerendered metadata, `sitemap.xml`, and `llms.txt` automatically.
