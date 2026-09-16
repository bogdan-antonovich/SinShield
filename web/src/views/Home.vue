<script setup lang="ts">
import AboutSection from '@/modules/main/components/AboutSection.vue'
import FaqSection from '@/modules/main/components/FaqSection.vue'
import HeroSection from '@/modules/main/components/HeroSection.vue'
import HowItWorksSection from '@/modules/main/components/HowItWorksSection.vue'
import WhatThatIsSection from '@/modules/main/components/WhatThatIsSection.vue';
import ProtectionSection from '@/modules/main/components/ProtectionSection.vue'
import AdvancedTuning from '@/modules/main/components/AdvancedTuning.vue'

defineOptions({ name: 'HomeView' })
</script>

<template>
  <main>
    <HeroSection />
    <AboutSection />
    <HowItWorksSection />
    <WhatThatIsSection />
    <ProtectionSection />
    <AdvancedTuning />
    <FaqSection />
  </main>
</template>
