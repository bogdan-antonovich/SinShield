<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'
import { authors } from '@/modules/main/authors'

defineOptions({ name: 'AuthorsView' })

usePageMetadata({
  title: 'Authors | SinShield',
  description:
    'Meet the author and reviewer responsible for researching, reviewing, updating, and correcting SinShield guides.',
  path: '/authors',
  image: '/sinshield-thumbnail.jpg',
})

const breadcrumbs = [{ label: 'Home', to: '/' }, { label: 'Authors' }]
</script>

<template>
  <main class="bg-white">
    <section class="py-20 pt-32 sm:py-28 sm:pt-40 lg:py-32 lg:pt-48" aria-label="Authors">
      <BaseContainer>
        <div class="mx-auto max-w-5xl">
          <TheBreadCrumbs :items="breadcrumbs" color="#082D48" />
          <h1
            class="mt-10 font-display text-4xl font-extrabold tracking-[-0.03em] text-navy sm:text-5xl"
          >
            Authors
          </h1>

          <div class="mt-10 sm:mt-12">
            <RouterLink
              v-for="author in authors"
              :key="author.path"
              :to="author.path"
              class="group grid overflow-hidden rounded-[2rem] bg-white shadow-xl shadow-primary/8 ring-1 ring-navy/8 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/40 md:grid-cols-[17rem_minmax(0,1fr)]"
            >
              <div class="grid min-h-64 place-items-center bg-navy p-10 md:min-h-80">
                <img
                  :src="author.image"
                  :alt="author.imageAlt"
                  width="256"
                  height="256"
                  class="w-full max-w-48 rounded-3xl shadow-lg shadow-black/15"
                />
              </div>

              <article class="flex flex-col justify-center p-8 sm:p-10 md:p-12">
                <h3
                  class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
                >
                  {{ author.name }}
                </h3>
                <p class="mt-5 text-lg leading-8 text-navy/70">
                  {{ author.description }}
                </p>
                <span
                  class="mt-7 inline-flex items-center gap-2 font-display font-bold text-primary underline-offset-4 group-hover:underline"
                >
                  View author profile
                </span>
              </article>
            </RouterLink>
          </div>
        </div>
      </BaseContainer>
    </section>
  </main>
</template>
