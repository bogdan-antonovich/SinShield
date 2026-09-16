// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import TableOfContents from '@/modules/blog/components/TableOfContents.vue'

type ObserverCallback = ConstructorParameters<typeof IntersectionObserver>[0]

let observerCallback: ObserverCallback

class IntersectionObserverStub {
  constructor(callback: ObserverCallback) {
    observerCallback = callback
  }

  observe() {}
  disconnect() {}
  unobserve() {}
  takeRecords() {
    return []
  }

  readonly root = null
  readonly rootMargin = ''
  readonly thresholds = []
}

afterEach(() => {
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})

describe('TableOfContents', () => {
  it('scrolls its list to keep the active page section visible', async () => {
    vi.stubGlobal('IntersectionObserver', IntersectionObserverStub)
    document.body.innerHTML = '<h2 id="first">First</h2><h2 id="last">Last</h2>'

    const wrapper = mount(TableOfContents, {
      attachTo: document.body,
      props: {
        entries: [
          { id: 'first', label: 'First' },
          { id: 'last', label: 'Last' },
        ],
      },
    })
    const scroller = wrapper.get('.toc-scroll').element as HTMLElement
    const lastLink = wrapper.get<HTMLElement>('[href="#last"]')
    const lastHeading = document.getElementById('last')
    const scrollTo = vi.fn<
      (optionsOrX?: ScrollToOptions | number, y?: number) => void
    >()

    expect(lastHeading).not.toBeNull()

    Object.defineProperties(scroller, {
      clientHeight: { value: 200 },
      scrollHeight: { value: 500 },
      scrollTop: { value: 0, writable: true },
    })
    scroller.scrollTo = scrollTo
    vi.spyOn(scroller, 'getBoundingClientRect').mockReturnValue({
      top: 100,
      bottom: 300,
    } as DOMRect)
    vi.spyOn(lastLink.element, 'getBoundingClientRect').mockReturnValue({
      top: 330,
      bottom: 360,
    } as DOMRect)

    observerCallback(
      [
        {
          target: lastHeading as HTMLElement,
          isIntersecting: true,
        } as unknown as IntersectionObserverEntry,
      ],
      {} as IntersectionObserver,
    )
    await nextTick()
    await nextTick()

    expect(lastLink.classes()).toContain('is-active')
    expect(scrollTo).toHaveBeenCalledWith({ top: 112, behavior: 'smooth' })

    wrapper.unmount()
  })
})
