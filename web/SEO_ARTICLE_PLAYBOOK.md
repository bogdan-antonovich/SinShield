# SinShield SEO article playbook

This document is the canonical SEO decision guide for planning, writing, reviewing, and maintaining SinShield articles. It converts the supplied Ahrefs SEO Fundamentals transcript into an operational workflow for this project.

Use it together with:

- [`src/modules/blog/AGENTS.md`](src/modules/blog/AGENTS.md) for editorial voice, article construction, research standards, and product-integrity rules;
- [`BLOGGING.md`](BLOGGING.md) for Markdown frontmatter, file placement, previewing, and publishing; and
- [`../PROJECT_OVERVIEW.md`](../PROJECT_OVERVIEW.md) for verified SinShield capabilities and technical details.

This playbook governs **SEO strategy**. The blog `AGENTS.md` governs **how the article should sound**. `BLOGGING.md` governs **how it is implemented**. If they conflict, follow the more specific repository instruction and flag the conflict instead of silently choosing.

## What this playbook is for

The purpose of an SEO article is not to place keywords into prose. It is to create the best realistic answer for a relevant search need, in a form searchers already demonstrate that they want, on a topic that can benefit SinShield and that the site has a credible chance of ranking for.

The complete model is:

1. Search engines must be able to discover, crawl, and index the page.
2. The page must match the reason behind the query.
3. The content must solve the searcher's problem with appropriate depth.
4. The site and page must have enough relevant authority to compete.
5. The topic must contribute to a real SinShield business or audience goal.

No single step compensates for failure at another. A technically perfect article that misses intent will struggle. A high-volume keyword with no business relevance wastes effort. Excellent writing cannot rank if the page is not indexed. Keyword research, content, links, and technical health are one system.

## Source limitations and current verification

The source transcript teaches durable fundamentals, but its examples, figures, tools, and descriptions of Google reflect the time when the course was recorded. Treat all numerical claims and platform-specific instructions in the transcript as examples, not current facts.

Before relying on any current search result, product feature, platform rule, price, policy, interface path, ranking-system statement, or research finding:

- verify it at the time the article is produced;
- prefer official documentation and primary research;
- record the access or publication date when it affects interpretation; and
- distinguish verified facts from editorial conclusions.

Ahrefs is a useful example throughout the transcript, not a mandatory tool. Equivalent reliable tools and direct SERP inspection are acceptable. Metrics such as search volume, traffic estimates, keyword difficulty, Domain Rating, URL Rating, and backlink counts are estimates. Use them to support judgment, never as truth or as automatic go/no-go decisions.

## The operating principles

### Optimize for the searcher, not a keyword counter

Search intent is the reason a person made the query. Search engines attempt to return the most relevant result, so satisfying that person is the central on-page objective.

Do not:

- stuff an exact-match phrase into the title, URL, headings, and body;
- repeat a keyword a prescribed number of times;
- add every related query verbatim;
- write to an arbitrary minimum word count; or
- lengthen a simple answer to make it appear comprehensive.

Use the main query when it reads naturally, then use the vocabulary required to explain the subject accurately. Content depth means completing the searcher's task and providing the context they reasonably need. It does not mean length for its own sake.

### Select topics, not isolated phrases

A strong page normally ranks for a family of closely related searches, not only one exact phrase. Evaluate the traffic potential of the whole topic by examining the total organic traffic and keyword coverage of relevant top-ranking pages. Raw search volume for one phrase can be misleading, especially when the SERP answers the query without a click.

One page should own one coherent search intent. Closely related wording usually belongs on that page. A meaningfully different intent, audience, format, or task may deserve another page.

### Business relevance is mandatory

Traffic is useful only when the people arriving could reasonably benefit from SinShield, learn something central to its mission, or move toward a relevant decision. The product must fit the topic organically; an unrelated traffic opportunity does not become valuable because a SinShield paragraph can be attached to it.

Use this business-potential scale:

| Score | Meaning for SinShield | Typical implication |
| --- | --- | --- |
| **3 — Direct** | SinShield naturally helps perform the task or solve a central part of the problem. | Highest priority when intent and ranking feasibility also work. Explain the fit and limitations plainly. |
| **2 — Adjacent** | The reader is evaluating approaches or tools and SinShield is one legitimate option or layer. | Strong opportunity for comparisons, alternatives, setup guides, and decision support. |
| **1 — Audience-relevant** | The topic matters to the intended audience, but mentioning SinShield would be secondary. | Publish selectively for topical coverage, education, or internal-link support. Do not force a sales pitch. |
| **0 — Unrelated** | The likely visitor has no meaningful connection to the product or mission. | Do not target for SEO merely because it has traffic. |

Business potential never licenses a misleading product claim. SinShield is an Android protection layer, not a cure, diagnosis, treatment, guarantee, accountability partner, or universal solution.

### Compete where success is plausible

Before investing in an article, inspect the pages and sites already ranking. Ranking difficulty is not a single score. It is a judgment based on:

- whether the top results actually match the query well;
- the quality and completeness of their content;
- the quantity and quality of referring domains to those pages;
- the authority and reputation of the ranking sites;
- their topical authority on the subject;
- the presence of weaker or similarly established sites in the results; and
- whether SinShield can produce something genuinely more useful or better evidenced.

Authority metrics are rough comparative signals. They are not Google metrics, and a lower-authority but highly relevant specialist site can outrank a stronger general site.

### Earn authority through value

Backlinks matter most when they are editorial, relevant, reputable, and contextually placed. Link building is relationship building with relevant people who may cite the page because it improves their own work.

Do not buy links, spam comments or directories, manipulate anchors, or send indiscriminate outreach. Links that are easy for anyone to create generally carry little editorial meaning. Never trade the site's long-term reputation for a short-term metric.

### Technical health is foundational

If a page cannot be discovered, crawled, rendered, or indexed, its content work cannot produce search traffic. Maintain a logical site hierarchy, internal links, correct indexation rules, a valid sitemap, deliberate redirects and canonicals, reasonable performance, and regular audits for broken pages, orphan pages, duplicates, and redirect chains.

## The required workflow

Do not begin drafting with only a topic name. Work through the following gates in order. A later gate may reveal that an earlier decision needs to change.

## Phase 1: Define the real problem and business case

Write a short topic hypothesis:

> People searching for **[query/topic]** are trying to **[task or decision]**. A useful SinShield article can help by **[reader outcome]**. This matters to the product because **[honest business connection]**.

Then answer:

- Who is searching, and what situation are they likely in?
- What must they know, decide, set up, compare, or change after reading?
- Is the query informational, comparative, commercial, navigational, or mixed?
- Which part of the problem can SinShield actually address?
- What can SinShield not address?
- What observable reader outcome would make the article successful?
- What business-potential score from 0–3 applies, and why?

Reject or reframe the topic if the product connection is artificial, the audience is wrong, or the article would require unsupported medical, psychological, legal, or product claims.

## Phase 2: Discover the search language

Generate candidate topics and queries from several sources rather than trusting one tool:

- broad seed terms tied to problems SinShield addresses;
- wording used by real users in questions, support discussions, reviews, and communities;
- Google suggestions, related searches, and People Also Ask where available;
- keywords and pages that bring relevant traffic to organic search competitors;
- gaps in existing SinShield coverage;
- recurring questions within current articles; and
- modifiers that reveal intent, such as `how`, `what`, `why`, `best`, `versus`, `alternative`, `review`, `block`, `disable`, or `stop`.

Organic search competitors are sites or pages ranking for the queries SinShield wants to serve. They are not necessarily direct product competitors.

Group candidate queries by shared intent. Do not create separate pages for trivial variations. Split them only when the SERPs or the reader tasks are materially different.

## Phase 3: Qualify the topic

Every proposed article needs a topic scorecard.

### Search demand

Determine whether people search for the subject. Record the estimated volume, geography, seasonality, trend, and source where available. Do not reject a strategically important question solely because a tool reports low volume; tools miss long-tail and emerging demand.

### Traffic potential

Inspect relevant top-ranking pages and estimate how much total organic traffic the topic could support across related queries. Check whether the SERP itself satisfies many searches without a click. Prefer topic-level opportunity over the headline volume of one keyword.

### Business potential

Assign the 0–3 score and write one sentence defending it. State where a product mention would naturally occur. If no natural placement exists, do not manufacture one.

### Intent match

Confirm that SinShield can publish the content type and format that searchers want. A blog article should not target a query dominated by product pages, app-store results, tools, videos, or another form unless there is evidence that an article can satisfy a meaningful part of the intent.

### Ranking feasibility

Answer these questions:

1. Do any high-ranking results fail to match the query closely?
2. Are any results outdated, thin, inaccurate, poorly structured, or unhelpful?
3. Can SinShield create something more useful through first-hand product knowledge, original testing, clearer explanations, stronger evidence, or a better decision framework?
4. Can the page realistically earn relevant backlinks or citations?
5. Is SinShield in a plausible authority range, or does the SERP contain a credible lower-authority specialist?
6. Does SinShield have, or can it build, topical authority around this subject?
7. Are there SERP features that materially reduce clicks or change the content format needed?

The more favorable answers there are, the stronger the opportunity. This is not a mechanical vote: a single severe issue, such as completely incompatible intent, can disqualify the topic.

### Go/no-go decision

Record one of:

- **Go:** valuable, matchable, and plausibly competitive now.
- **Go as support:** useful for readers or topical coverage, despite modest direct traffic or business value.
- **Defer:** valuable but unrealistic until the site gains authority, evidence, product support, or a stronger content asset.
- **Reject:** wrong intent, weak relevance, negligible opportunity, unsafe premise, or no honest route to a better result.

## Phase 4: Decode search intent with the three Cs

Search the primary query in the target market and inspect at least the leading relevant results. Do not infer intent from the wording alone.

### 1. Content type

Identify what dominates:

- blog articles;
- product or category pages;
- landing pages or tools;
- videos;
- forums or community discussions;
- news or research; or
- a mixed SERP.

### 2. Content format

For article-like results, identify the expected format:

- how-to guide;
- step-by-step tutorial;
- list or roundup;
- comparison;
- alternatives page;
- definition or explainer;
- opinion or analysis;
- research summary; or
- troubleshooting guide.

### 3. Content angle

Identify the recurring promise or hook, such as:

- current or recently updated;
- for beginners;
- private;
- Android-specific;
- fastest or simplest;
- evidence-based;
- free or low-cost; or
- designed for a particular situation.

The angle is usually less stable than type and format. Follow it only when it reflects a genuine user need. Never imitate a freshness angle by changing the year without materially reviewing the article.

### Resolve mixed SERPs

When the SERP is mixed, determine whether it represents multiple legitimate intents or unstable results. Look at which type dominates the top positions, whether article results intentionally target the query, and which format SinShield can satisfy best. Do not treat a single outlier as permission to ignore the dominant intent.

Record the conclusion in this form:

> Create a **[content type]** in **[format]** form, using a **[angle]** angle, for **[audience/situation]**, because **[SERP evidence and reader logic]**.

## Phase 5: Build the evidence-backed content brief

Study roughly three to five relevant high-ranking pages that match the dominant intent. Exclude obvious outliers. The objective is to understand the minimum useful answer and find a defensible way to improve it—not to merge competitors into a derivative article.

For each page, note:

- the question it answers first;
- its section structure and recurring subtopics;
- information present across several results;
- important omissions or weak explanations;
- distinctive evidence, examples, tools, or experience;
- the intended audience and assumed knowledge;
- content that is stale or unsupported; and
- why a searcher might still be dissatisfied.

Use keyword/content-gap tools where useful to reveal shared subtopics and the language searchers use. Treat their output as research prompts, not a checklist of phrases to insert.

The brief must contain:

1. Primary topic and representative query.
2. Secondary queries grouped by reader need.
3. Searcher and situation.
4. Desired reader outcome.
5. Business-potential score and natural product role.
6. Three-Cs intent conclusion.
7. Ranking-feasibility conclusion.
8. Required questions and subtopics.
9. Evidence and sources needed.
10. Original contribution or information gain.
11. Proposed title, slug, description, and outline.
12. Relevant existing pages for internal links to and from the article.
13. Claims that must not be made.
14. A provisional maintenance or refresh trigger.

### Require information gain

The article needs a reason to exist beyond paraphrasing pages that already rank. At least one meaningful advantage should be planned:

- verified first-hand testing;
- original product or technical knowledge;
- a clearer process or decision framework;
- better primary evidence;
- a more specific audience or scenario supported by the SERP;
- current screenshots or platform steps;
- an honest comparison competitors avoid;
- practical limitations and failure cases; or
- a concise answer where competitors bury the solution.

If no genuine advantage can be named, improve the research or reconsider the topic.

## Phase 6: Write the answer

Follow the editorial brief in `src/modules/blog/AGENTS.md`. From an SEO perspective, the draft must also do the following.

### Answer early

Confirm the reader is in the right place and give the central answer or decision near the beginning. Do not delay the useful information with history, generic scene-setting, or a dictionary definition.

### Cover the task from start to finish

Include the context and subtopics a reasonable reader needs to complete the task or make the decision. Omit tangents that exist only because they contain adjacent keywords.

### Use natural semantic coverage

Use accurate terms, entities, synonyms, and related concepts because the subject requires them. Do not force exact-match phrases or awkward variants. A page can rank for queries it never repeats verbatim when it answers their shared intent well.

### Make headings descriptive

Headings should tell skimming readers what each section answers. They should not be vague, sensational, or written merely to house a keyword.

### Choose length from the task

Write as much as the reader needs and no more. Competitor length can expose expected depth, but it is not a target. A complete short answer is better than an inflated long one.

### Make the product connection honest

Introduce SinShield only where it advances the reader's task. Explain the mechanism, consequence, and limitation. Never imply that installing an app resolves every behavioral, relationship, medical, or psychological dimension of the problem.

## Phase 7: Apply on-page SEO

Content and intent come first. Complete these tangible optimizations after the answer is sound.

### Title

- Describe the page accurately and make its benefit clear.
- Include the primary topic naturally when useful.
- Match the format and angle actually delivered.
- Do not use a year unless freshness matters and the content is maintained.
- Avoid clickbait and guarantees.

### URL slug

- Keep it short, descriptive, lowercase, and hyphenated.
- Use the stable topic rather than unnecessary dates or filler.
- Do not change an existing published slug casually; a redirect and link review would be required.

### Description

- Summarize the answer directly for a potential search visitor.
- Treat it as click-through copy, not a ranking trick.
- Include the topic naturally if it helps clarity.
- Do not promise information the page does not contain.
- Expect search engines sometimes to generate a different snippet.

### Headings and body

- Use one clear page title and a logical `##`/`###` hierarchy.
- Include phrases naturally, never by quota.
- Keep paragraphs and sentences readable on mobile.
- Prefer plain language to impressive vocabulary.
- Use lists or tables only when they make the task easier to scan.

### Internal links

Add contextual links:

- **to the new page** from relevant existing articles; and
- **from the new page** to useful product and supporting articles.

Use descriptive, natural anchor text. Internal links should help a reader take the next logical step and help search engines understand topic relationships. Ensure the article is not orphaned.

### External links and citations

Link to authoritative sources that substantiate material claims. Prefer primary research, official policies, and official product documentation. A citation should support the exact nearby claim and the prose should explain what it means for the reader.

### Images

When an existing image is genuinely useful:

- use a descriptive file name;
- provide concise, meaningful alt text that describes the image in context;
- do not stuff keywords into alt text;
- compress the file and use an appropriate format and dimensions; and
- avoid decorative images that slow the page without helping comprehension.

Follow the blog instruction not to generate new images unless the user explicitly changes that requirement.

### Structured and social metadata

Use supported Open Graph and structured-data mechanisms only when they accurately represent visible page content. Never add schema solely to pursue a rich result that the page does not qualify for.

## Phase 8: Run the pre-publication gates

An article is not ready until all applicable checks pass.

### Strategy gate

- The primary topic, audience, and reader outcome are explicit.
- Search demand and topic-level traffic potential were considered.
- Business potential has a justified 0–3 score.
- The page matches the dominant content type, format, and appropriate angle.
- Ranking feasibility was assessed from the actual SERP.
- The article has a specific reason to exist.

### Content gate

- The opening answers the central question promptly.
- The article completes the reader's task with appropriate depth.
- Important subtopics from the research are addressed without copying competitor structure or language.
- Claims are current, sourced, and proportionate to the evidence.
- SinShield's role and limitations are accurate.
- The draft contains no keyword stuffing, arbitrary padding, or generic SEO filler.
- The editorial quality check in `src/modules/blog/AGENTS.md` passes.

### On-page gate

- Title, slug, description, and headings accurately describe the page.
- The primary topic appears naturally where useful.
- Internal links exist in both directions where relevant.
- External citations use descriptive anchors and reliable sources.
- Existing images have descriptive alt text and reasonable file size.
- The page is readable on desktop and mobile.

### Technical gate

- Frontmatter is valid and the slug matches the filename.
- The page is not accidentally marked `noindex` or blocked from crawling.
- The canonical URL resolves to the intended preferred page.
- The page is included in the site's discoverable structure and sitemap process.
- Links resolve without unintended redirects or errors.
- The production build passes.
- The rendered article, metadata, table of contents, and callouts are visually checked.

## Phase 9: Earn discovery and authority

Publishing is not the finish line. Choose promotion based on the quality and linkability of the asset.

### Decide whether outreach is warranted

Outreach is best reserved for the strongest work: original data, testing, expert explanation, a useful tool or framework, a uniquely current resource, or a genuinely superior reference. A routine explainer may be worth publishing without being worth a campaign.

### Evaluate backlink prospects

Prioritize pages and sites with:

- topical relevance;
- real editorial standards and organic visibility;
- a contextual reason to cite the article;
- page-level authority or an audience that matters; and
- no signs of link selling, spam, or manipulative schemes.

The ideal backlink is editorially placed on a relevant and authoritative page, uses a natural descriptive anchor, and is followed. Much of this is outside SinShield's control; focus on the quality of the asset and prospect rather than demanding a particular anchor or link attribute.

### Use the three-stage outreach process

1. **Prospect:** find relevant people and pages that may benefit from the asset.
2. **Vet:** confirm relevance, quality, reputation, and a real reason to contact them.
3. **Reach out:** send a concise, individual message built around value.

Use a targeted “sniper” approach, not a mass “shotgun” broadcast.

### Anatomy of a useful outreach message

1. **Subject:** accurate, brief, and specific enough to earn an open without clickbait.
2. **Reason for contact:** show why this person or page is relevant.
3. **Qualification and justification:** explain why SinShield has something credible to contribute.
4. **Ask and value proposition:** make the request clear and show how it helps their audience or improves their page.
5. **Conversation opener:** finish with a low-pressure question rather than an entitlement to a link.

Personalization means understanding the recipient and the value exchange, not inserting a first name into a template. The first objective is often to start a useful relationship. Do not repeatedly chase people with near-identical messages.

Acceptable approaches can include expert-source contributions, relevant guest articles, and outreach around a demonstrably stronger resource. Reassess any tactic when its platform, policy, or ethics have changed.

## Phase 10: Maintain and improve

SEO compounds only when pages and the site remain useful and technically healthy.

For each article, define what would make it stale:

- a product or platform interface changes;
- a price, policy, law, feature, or availability changes;
- new primary research changes the evidence;
- search intent or the dominant SERP format shifts;
- SinShield's capabilities change;
- links break; or
- performance declines materially.

Review performance with appropriate data, such as impressions, clicks, queries, click-through rate, average position, conversions, backlinks, and user behavior. Diagnose before editing:

- **Impressions but few clicks:** inspect title, description, ranking position, SERP features, and intent alignment.
- **Traffic but weak engagement or outcomes:** inspect whether the article fulfills the promise and attracts the right audience.
- **Ranking decline:** re-check intent, freshness, competitor improvements, lost links, cannibalization, and technical issues.
- **No impressions:** verify indexation, internal linking, keyword/topic assumptions, and realistic competitiveness.

Do not refresh mechanically. A new date without meaningful review is not freshness.

### Site-level technical maintenance

Run recurring site audits and investigate, at minimum:

- accidental `noindex` directives;
- harmful `robots.txt` rules;
- missing or invalid sitemap entries;
- broken internal and external links;
- orphan pages;
- duplicate or near-duplicate pages;
- incorrect canonicals;
- redirect chains and outdated redirects;
- slow or oversized assets; and
- a site hierarchy that has become difficult to crawl or understand.

Redirect retired URLs to the most relevant replacement when one exists so readers and accumulated signals reach the correct destination. Canonicals indicate a preferred duplicate but are not a substitute for sound URL and redirect handling, and search engines may choose a different canonical.

## The reusable article command

Use the prompt below when asking an assistant to create or substantially revise a SinShield SEO article. Replace the bracketed input. If information is missing, the assistant should research it or state what cannot be established; it must not invent evidence.

```text
Create a publication-ready SinShield SEO article about: [TOPIC OR READER PROBLEM].

Primary market/language: [MARKET AND LANGUAGE, default: current SinShield target market and English]
Known target query, if any: [QUERY OR "discover it"]
Desired article type, if any: [GUIDE / COMPARISON / EXPLAINER / TROUBLESHOOTING / discover it]
Special constraints or sources: [INPUT]

Treat web/SEO_ARTICLE_PLAYBOOK.md as the SEO process, web/src/modules/blog/AGENTS.md as the binding editorial brief, web/BLOGGING.md as the publishing specification, and PROJECT_OVERVIEW.md plus the current codebase as the source of truth for SinShield capabilities.

Work in two stages.

STAGE 1 — RESEARCH AND DECISION
1. Define the searcher, problem, desired outcome, and honest connection to SinShield.
2. Discover and group the relevant query family by shared intent.
3. Evaluate search demand, topic-level traffic potential, business potential (0–3), intent match, and ranking feasibility.
4. Inspect the current SERP and identify the dominant content type, format, and angle using the three Cs.
5. Analyze relevant leading pages for necessary subtopics, weaknesses, evidence gaps, and opportunities for information gain. Do not copy their wording or blindly merge their outlines.
6. Identify current primary/official sources for all material claims and time-sensitive details.
7. Identify relevant SinShield pages for internal links to and from the article.
8. Produce a concise decision record with: go/defer/reject, reasoning, primary topic, intent conclusion, business score, reader outcome, original contribution, evidence plan, outline, title, slug, description, and maintenance trigger.
9. If the decision is defer or reject, stop and explain what would need to change. Do not manufacture an article.

STAGE 2 — ARTICLE AND VALIDATION
10. Write the article only after the topic passes Stage 1. Answer the central question early, satisfy the searcher's task from start to finish, and use only as much length as the task requires.
11. Apply natural on-page SEO: accurate title and description, short stable slug, descriptive headings, useful internal/external links, and contextual image alt text where existing images are used. Never stuff keywords or target a word count.
12. Describe SinShield accurately and transparently. Explain what it changes in practice and what it cannot guarantee. Do not invent capabilities or make medical, psychological, legal, or recovery promises.
13. Validate every current policy, setting path, price, product capability, platform rule, and research claim using authoritative sources.
14. Run every applicable strategy, content, on-page, editorial, and technical gate in the playbook.
15. Create the Markdown file in web/src/modules/blog/content using the repository format. Do not generate an image; use an appropriate existing asset only when one is available and relevant.
16. Preview the rendered article and run the production build. Report the created file, validation performed, unresolved limitations, and any recommended future outreach or maintenance.

Never optimize for a keyword at the expense of the reader, factual accuracy, product honesty, or realistic ranking judgment.
```

## Compact planning template

Use this template to preserve the reasoning behind every article. It can live in working notes; it does not need to appear in the published Markdown.

```md
# SEO decision record: [working title]

## Problem and reader
- Searcher:
- Situation:
- Task/decision:
- Desired outcome:
- Natural SinShield role:
- SinShield limitation relevant here:

## Topic economics
- Primary topic/query:
- Related intent cluster:
- Target market:
- Search demand and source:
- Traffic potential and source:
- Business potential (0–3) and reason:
- SERP click limitations/features:

## Intent: three Cs
- Content type:
- Content format:
- Content angle:
- Evidence from current results:

## Ranking feasibility
- Intent/content weaknesses in current results:
- Referring-domain landscape:
- Site-authority landscape:
- Topical-authority comparison:
- Our defensible advantage/information gain:
- Linkability:
- Verdict: Go / Go as support / Defer / Reject

## Brief
- Central answer:
- Required subtopics:
- Questions to answer:
- Primary/official sources:
- Claims requiring special care:
- Proposed outline:
- Title:
- Slug:
- Description:
- Internal links from existing pages:
- Internal links to supporting pages:
- Existing image, if relevant:
- Refresh trigger:

## Validation
- Editorial brief passed:
- Factual/source review passed:
- On-page checks passed:
- Technical checks passed:
- Render reviewed:
- Production build passed:
```

## Final doctrine

The priority order is:

1. Help the right reader solve the right problem.
2. Tell the truth and preserve the reader's agency.
3. Choose topics that matter to SinShield.
4. Match demonstrated search intent.
5. Contribute something worth choosing and citing.
6. Make the page clear to people and search engines.
7. Keep it discoverable, fast, linked, current, and technically healthy.

SEO is not a layer applied after writing. It is the discipline of choosing a valuable problem, understanding the demand behind it, building the right answer, earning trust, and maintaining the result.
