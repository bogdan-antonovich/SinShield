<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'

defineOptions({ name: 'PrivacyPolicyView' })

usePageMetadata({
  title: 'Privacy Policy | SinShield',
  description:
    'Learn how the SinShield Android app and website handle information and protect your privacy.',
  path: '/privacy-policy',
})
</script>

<template>
  <main class="bg-white py-32 text-navy sm:py-36">
    <BaseContainer>
      <article class="mx-auto max-w-3xl space-y-8 text-base leading-7">
        <header>
          <h1 class="font-display text-4xl font-extrabold tracking-tight sm:text-5xl">
            Privacy Policy
          </h1>
          <p class="mt-3 text-navy/65">Effective date: September 14, 2026</p>
        </header>

        <section>
          <h2 class="font-display text-2xl font-bold">1. Overview</h2>
          <p class="mt-3">
            SinShield is designed to block adult content while keeping screen analysis on your
            Android device. This Privacy Policy explains how the SinShield Android app and the
            SinShield website handle information.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">2. Information processed by the app</h2>
          <p class="mt-3">
            SinShield does not require an account. To provide screen protection, the app uses an
            Android Accessibility Service to observe supported app changes, read limited on-screen
            interface context, and capture screenshots for adult-content detection. Screenshots
            and detection results are processed by machine-learning models on your device and are
            not sent to SinShield.
          </p>
          <p class="mt-3">
            Screenshots used for routine detection are temporary. If you mark a block as “Not
            disturbing,” the app stores the relevant screenshot, app package name, detection
            result, settings, and diagnostic details locally on your device so it can remember the
            correction. This evidence is not uploaded automatically and is kept outside Android's
            automatic backup area.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">3. Website protection and DNS</h2>
          <p class="mt-3">
            Website protection uses Android's local VPN feature to inspect standard DNS requests.
            Requests matching the on-device adult-domain list are blocked locally. Other DNS
            requests are forwarded to the DNS resolver already configured for your network.
            SinShield does not operate a remote VPN, receive your DNS history, decrypt web traffic,
            or inspect the contents of websites you visit.
          </p>
          <p class="mt-3">
            Your network provider or DNS resolver may process allowed DNS requests under its own
            privacy practices.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">4. Information stored on your device</h2>
          <p class="mt-3">The app stores information needed to provide and configure protection, including:</p>
          <ul class="mt-3 list-disc space-y-2 pl-6">
            <li>protection settings, permission status, and service health information;</li>
            <li>the adult-domain blocklist, its last update time, and domains you add;</li>
            <li>locally remembered false-positive corrections; and</li>
            <li>optional debugging information when debugging features are enabled.</li>
          </ul>
          <p class="mt-3">
            Some settings may be included in Android device backups depending on your device and
            Google account settings. You can remove app data through Android settings or by
            uninstalling SinShield.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">5. External connections</h2>
          <p class="mt-3">
            The app periodically downloads an adult-domain list from the StevenBlack hosts project
            on GitHub. That request may expose standard connection information, such as your IP
            address and the app's user-agent, to GitHub and its infrastructure. It does not include
            screenshots, your DNS requests, or your browsing history.
          </p>
          <p class="mt-3">
            If you download SinShield through an app store, that store processes information under
            its own privacy policy.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">6. Website and support</h2>
          <p class="mt-3">
            The SinShield website does not intentionally use advertising trackers or analytics
            cookies. Our hosting provider may process standard request information, such as IP
            address, browser type, requested page, and time of access, to deliver and secure the
            site.
          </p>
          <p class="mt-3">
            If you email support, we receive your email address and any information you choose to
            include. We use it to respond to your request and keep it only as long as reasonably
            necessary for support, security, and legal purposes.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">7. Sharing and sale of information</h2>
          <p class="mt-3">
            SinShield does not sell personal information. We do not receive or share the screen
            content or DNS history processed locally by the app. Information you send to support,
            and limited website request data, may be handled by service providers that help us
            operate email and website hosting. We may also disclose information when required by
            law or necessary to protect rights, safety, and security.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">8. Security and children</h2>
          <p class="mt-3">
            We use reasonable safeguards, but no device, transmission, or storage system is
            completely secure. SinShield is not designed to collect personal information from
            children. If you believe a child sent personal information to support, contact us so we
            can review and delete it where appropriate.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">9. Changes to this policy</h2>
          <p class="mt-3">
            We may update this policy as SinShield changes. We will post the updated version here
            and revise the effective date. Material changes may also be communicated through the
            app or website when appropriate.
          </p>
        </section>

        <section>
          <h2 class="font-display text-2xl font-bold">10. Contact</h2>
          <p class="mt-3">
            Questions or privacy requests can be sent to
            <a class="text-primary underline" href="mailto:support@sinshield.app">
              support@sinshield.app</a
            >.
          </p>
        </section>
      </article>
    </BaseContainer>
  </main>
</template>
