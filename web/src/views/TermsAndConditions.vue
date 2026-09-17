<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'

defineOptions({ name: 'TermsAndConditionsView' })

usePageMetadata({
  title: 'Terms & Conditions | SinShield',
  description: 'Read the terms and conditions governing use of the SinShield app and website.',
  path: '/terms-and-conditions',
})
</script>

<template>
  <main class="bg-white py-32 text-navy sm:py-36">
    <BaseContainer>
      <article class="mx-auto max-w-3xl space-y-8 text-base leading-7">
        <header>
          <h1 class="font-display text-4xl font-extrabold tracking-tight sm:text-5xl">
            Terms &amp; Conditions
          </h1>
          <p class="mt-3 text-navy/65">Effective date: September 14, 2026</p>
        </header>

        <section>
          <h2 class="font-display text-2xl font-bold">1. Acceptance of these terms</h2>
          <p class="mt-3">
            These Terms &amp; Conditions govern your use of the SinShield Android app and website.
            By installing, accessing, or using SinShield, you agree to these terms and the
            <RouterLink class="text-primary underline" to="/privacy-policy">Privacy Policy</RouterLink>.
            If you do not agree, do not use SinShield.
          </p>
          <p class="mt-3">
            You must be legally able to agree to these terms. If you use SinShield for a child or
            another person under your care, you are responsible for their use of the service.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">2. What SinShield provides</h2>
          <p class="mt-3">
            SinShield provides on-device tools intended to detect and cover adult content in
            supported Android apps and to block known adult-content domains using a local DNS VPN.
            Features may vary by device, Android version, app, website, network, and configuration.
          </p>
          <p class="mt-3">
            Content detection and domain lists are not perfect. SinShield may fail to block some
            content or may block content that is not adult content. It is a supplemental tool, not
            a guarantee that all unwanted content will be detected and not a substitute for
            supervision, device controls, or your own judgment.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">3. Permissions and device access</h2>
          <p class="mt-3">
            Core features require Android permissions and services, including Accessibility,
            display over other apps, notifications, and VPN access. You choose whether to grant
            these permissions and can revoke them in Android settings. Revoking a permission may
            disable the related feature. You are responsible for using SinShield only on devices
            you own or are authorized to manage.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">4. License</h2>
          <p class="mt-3">
            Subject to these terms, SinShield grants you a limited, personal, non-exclusive,
            non-transferable, and revocable license to use the app for its intended purpose. The
            app and website, excluding identified third-party materials, remain the property of
            SinShield and its licensors.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">5. Acceptable use</h2>
          <p class="mt-3">You may not:</p>
          <ul class="mt-3 list-disc space-y-2 pl-6">
            <li>use SinShield unlawfully or to violate another person's rights;</li>
            <li>interfere with, disrupt, or attempt to gain unauthorized access to the service;</li>
            <li>misrepresent your relationship with SinShield; or</li>
            <li>
              copy, distribute, sell, or exploit the app or website except as permitted by law or
              an applicable open-source license.
            </li>
          </ul>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">6. Third-party services and materials</h2>
          <p class="mt-3">
            SinShield may rely on third-party platforms and open-source materials, including app
            stores, Android system services, machine-learning runtimes and models, and a
            third-party adult-domain list. Their own terms and licenses may apply. SinShield is not
            responsible for third-party services, content, availability, or privacy practices.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">7. Updates and availability</h2>
          <p class="mt-3">
            We may update, add, remove, suspend, or discontinue features at any time. Updates may
            be required for continued use. We do not promise that SinShield will always be
            available, compatible with every device or app, or free from errors.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">8. Disclaimers</h2>
          <p class="mt-3">
            To the fullest extent permitted by law, SinShield is provided “as is” and “as
            available,” without warranties of any kind, whether express, implied, or statutory,
            including warranties of merchantability, fitness for a particular purpose,
            non-infringement, accuracy, and uninterrupted availability. Some jurisdictions do not
            allow certain warranty exclusions, so some of these exclusions may not apply to you.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">9. Limitation of liability</h2>
          <p class="mt-3">
            To the fullest extent permitted by law, SinShield and its contributors and service
            providers will not be liable for indirect, incidental, special, consequential,
            exemplary, or punitive damages, or for loss of data, use, profits, or goodwill,
            arising from your use of or inability to use SinShield. Nothing in these terms excludes
            liability that cannot legally be excluded or limited.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">10. Suspension and termination</h2>
          <p class="mt-3">
            You may stop using SinShield at any time by disabling its services and uninstalling the
            app. We may suspend access to hosted parts of the service if necessary to protect the
            service, users, or others, or if these terms are materially violated. Sections that by
            their nature should survive termination will remain effective.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">11. Changes to these terms</h2>
          <p class="mt-3">
            We may update these terms as SinShield changes. The updated terms will be posted here
            with a revised effective date. By continuing to use SinShield after updated terms take
            effect, you accept the updated terms to the extent permitted by law.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">12. General</h2>
          <p class="mt-3">
            If any provision of these terms is found unenforceable, the remaining provisions will
            remain in effect. A failure to enforce a provision is not a waiver. These terms,
            together with the Privacy Policy and any terms presented in the app, form the agreement
            between you and SinShield regarding the service.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">13. Contact</h2>
          <p class="mt-3">
            Questions about these terms can be sent to
            <a class="text-primary underline" href="mailto:support@sinshield.app">
              support@sinshield.app</a
            >.
          </p>
        </section>
      </article>
    </BaseContainer>
  </main>
</template>
