# SinShield Blog Article Prompt

Write a publication-ready SinShield blog article that matches the editorial voice and construction of the existing articles **“Porn on X”** and **“Accountability Apps.”** Use the instructions below as the complete style brief. Match the style, not the wording: do not recycle distinctive sentences, metaphors, examples, or claims from those articles unless they are independently relevant and verified for the new topic.

## Editorial goal

Help a reader who wants to avoid pornography or regain control of a digital habit make a practical decision without shame, panic, moralizing, or false promises. Write as a calm, informed guide who understands that the reader often makes a good decision with a clear head and struggles to keep it in a difficult moment. Preserve the reader's agency throughout: technology adds friction, removes easy access, or supports a decision; it does not magically cure the person or make relapse impossible.

The article should feel humane first, useful second, and commercial only where SinShield is genuinely relevant. It may recommend SinShield clearly, but it must acknowledge limitations and competing tools honestly.

## Voice and tone

- Address the reader directly as **you**. Use **we** only for transparent editorial or product disclosures, such as explaining how options were compared or that SinShield is our product.
- Open with the reader's likely situation, intent, or frustration rather than a generic definition. Show that you understand why they searched for the topic.
- Be empathetic without becoming sentimental. Never scold, diagnose, frighten, preach, or imply that an unwanted exposure is a moral failure.
- Use confident, plain English. State the useful answer early, then explain its boundaries and consequences.
- Favor nuance over absolutes. Phrases such as “a useful first layer,” “cannot promise a perfectly clean feed,” and “works best when paired with” reflect the desired degree of honesty, but do not copy them mechanically.
- Keep the tone steady and adult. Avoid hype, slang, jokes, exclamation marks, macho language, therapy clichés, and exaggerated recovery claims.
- When discussing an unexpected trigger, distinguish between what happened to the reader and what they choose next. Give them a short, concrete next action without blame.

## Sentence and paragraph style

- Write flowing, idea-dense sentences, often joining two or three closely related clauses with **because**, **while**, **which means**, **whereas**, **so**, or **but**. The prose should make the causal chain easy to follow.
- Balance those longer sentences with short, direct answers at important moments, especially at the start of a section or FAQ response.
- Most body paragraphs should contain two substantial sentences. Avoid both choppy fragments and walls of text.
- Build transitions into the reasoning. A paragraph should not merely list a feature; it should explain why that feature matters during an actual moment of temptation, accidental exposure, or decision-making.
- Prefer concrete verbs such as **block**, **cover**, **hide**, **report**, **open**, **leave**, **mute**, and **choose** over abstract corporate language.
- Use parallel contrasts when they clarify a decision: prevention versus reporting, private protection versus partner accountability, the calm moment versus the difficult moment, or platform controls versus device-level protection.
- Use occasional restrained imagery about friction, doors, paths, layers, loops, or holding the line, but do not stack metaphors or turn them into slogans.

## Required article shape

Adapt the sections to the topic, but follow this narrative progression:

1. **Frontmatter.** Begin with YAML containing `slug`, `title`, `description`, `author`, `publishedAt`, `readingTime`, `thumbnail`, `thumbnailAlt`, and `tags`. Add `featured` only when requested. Use `author: "SinShield Editorial"`. Make the title specific and search-friendly, and make the description a direct summary rather than a teaser.
2. **Empathetic opening.** In one or two paragraphs, name why the reader is here, give the central answer or tension, and explain why a simple-looking solution is incomplete. Do not begin with “In today's digital world,” a dictionary definition, or a statistic.
3. **Orientation.** Explain the key distinction the reader needs before acting. For an explainer, this may be what a platform allows versus what its filters catch. For a comparison, it may be the major categories of tools and the different jobs they perform.
4. **Evidence or method.** When relevant, briefly explain what research supports the advice or how products were evaluated. Translate evidence into its practical meaning rather than dropping citations into the article without interpretation.
5. **Main practical body.** Organize the answer with descriptive `##` headings. Use numbered `###` steps for a setup guide or numbered product sections for a roundup. Move from built-in, immediate, or simplest measures toward additional layers of protection.
6. **Decision support.** Explain which option or combination fits which reader. Make tradeoffs explicit and remind the reader that multiple layers often cover different failure points.
7. **FAQ.** Answer likely search questions with an immediate answer in the first sentence, followed by a short qualification. Do not use the FAQ to repeat whole sections verbatim.
8. **Where to start.** End with one manageable action the reader can take while their intention is clear. Follow it with a brief, relevant SinShield call to action.

Do not force every article to use the same headings. Preserve the progression while choosing headings that answer the actual search intent.

## Formatting patterns

- Use `##` for major sections and `###` for individual steps, products, or FAQ questions.
- Use bold lead-ins in bullets when a list compares routes, criteria, benefits, or reader profiles. Each bullet should explain why the item matters, not merely name it.
- Use Markdown tables only when readers need to scan the same attributes across several options. Introduce the table in one natural sentence and qualify time-sensitive prices or availability immediately after it.
- Use `[!NOTE]` callouts sparingly for a short answer, a candid disclosure, a strongest-setup summary, or a bottom line. Format them exactly like this:

  ```md
  > [!NOTE] Bottom line
  > One concise conclusion written as a complete sentence.
  ```

- In a ranked roundup, give every product the same basic treatment: what it is and how it works, two to four meaningful strengths, a bold **Where it falls short:** paragraph, and a short **Bottom line** callout.
- Link descriptive anchor text, not “click here.” Internal product links should be relevant to the sentence rather than inserted as standalone promotion.

P.S. DO NOT GENERATE ANY IMAGES. Use already existing onces

## Research and accuracy

- Verify every current policy, setting path, price, platform, product capability, age rule, and research finding against a reliable source before writing. Prefer official policies, official product documentation, and primary research.
- Make clear when a figure is approximate or current only at the time of writing. Never present changeable product details as timeless facts.
- Explain what a source means for the reader. Do not overstate correlation, habit research, or the effectiveness of accountability tools.
- Distinguish clearly between content that a platform permits, content it restricts, content its filters may miss, and content that violates its rules.
- Do not claim that any blocker is impossible to bypass. Say what it changes in practice: access, immediacy, visibility, reporting, privacy, or the time available to reconsider.
- Do not invent SinShield capabilities. When relevant and verified, describe its central position consistently: private Android protection, on-device analysis, screen-level covering of explicit or suggestive imagery, and network-level blocking of known adult sites. State plainly that it is Android-only and does not provide human accountability reports.

## Comparison integrity

When SinShield appears beside competitors, disclose that it is our product before or near the comparison. Give competitors credit for the jobs they perform better, describe their intended users fairly, and include meaningful drawbacks for SinShield as well as for every alternative. Ranking language must follow from explicit criteria, not from brand ownership.

Do not imply that automatic protection and human accountability are interchangeable. Describe the mechanism that creates accountability: a person seeing a report, an immediate intervention, a structured program, a financial stake, or another concrete consequence.

## What to avoid

- Generic SEO filler, repeated keywords, and headings that promise more than the section delivers.
- Shame-based language, religious assumptions, moral verdicts, or claims that all readers share the same motivation.
- Clinical claims or mental-health diagnoses unless the article specifically requires them and they are properly sourced.
- Vague advice such as “stay strong,” “use willpower,” or “practice self-care” without an observable action.
- Feature dumps that do not explain the real-world consequence of each feature.
- Pretending one toggle, app, partner, or routine is a complete solution.
- Disguising advertising as neutral reporting or hiding SinShield's limits.
- Overusing callouts, tables, bullets, or repeated “bottom line” summaries outside a comparison format.

## Final quality check

Before returning the article, confirm that it:

- answers the primary search question within the opening;
- sounds compassionate without sounding soft or vague;
- connects each recommendation to a real reader situation;
- separates verified fact from interpretation;
- includes candid limitations and no impossible guarantees;
- uses long, controlled sentences without becoming difficult to read;
- gives the reader a realistic next step;
- introduces SinShield naturally and transparently; and
- contains no copied phrasing from the reference articles.

Return only the finished Markdown article unless the user asks for research notes, alternatives, or an outline.
