<script setup lang="ts">
import InteractiveShowcaseSection from '@/common/components/InteractiveShowcaseSection.vue'

const steps = [
  {
    title: 'A screen change starts a scan',
    description:
      'When a protected app reports a window or content change, SinShield schedules a fresh capture. Rapid changes are combined so the scanner analyzes the newest visible frame instead of queuing stale frames.',
    illustration: '/images/how-it-works/scan-starts.svg',
    illustrationAlt: 'A scanning frame with a horizontal scan line',
  },
  {
    title: 'The visible frame is analyzed',
    description:
      'The captured frame is classified on the device. SinShield scores both the full screen and detected media regions for explicit and suggestive content; unchanged known-safe frames are skipped.',
    illustration: '/images/how-it-works/frame-analyzed.svg',
    illustrationAlt: 'A magnifying glass inspecting a framed area',
  },
  {
    title: 'Uncertain detections are checked again',
    description:
      'A clear unsafe score produces a final verdict. Borderline detections receive a provisional cover and a quick follow-up scan; an independent verifier is used when the policy requires another signal.',
    illustration: '/images/how-it-works/checked-again.svg',
    illustrationAlt: 'Circular arrows rechecking an uncertain result',
  },
  {
    title: 'Unsafe content is covered',
    description:
      'A final unsafe verdict places a full-screen shield in supported apps or a cover over the detected media region. Separately, the local DNS filter rejects requests for known adult domains before those sites can load.',
    illustration: '/images/how-it-works/content-covered.svg',
    illustrationAlt: 'A protective shield covering unsafe content',
  },
]
</script>

<template>
  <InteractiveShowcaseSection
    id="how-it-works"
    heading="How SinShield Works"
    description="Inside protected apps, SinShield responds to screen changes, captures the current frame, analyzes it with on-device models, and covers content that receives an unsafe verdict. Website protection checks DNS requests against an adult-domain list and rejects matches before the site loads."
    :items="steps"
    tablist-label="How SinShield works"
  />
</template>
