export default {
  async fetch(request, env) {
    const secure = (response) => {
      const headers = new Headers(response.headers)
      headers.set('Strict-Transport-Security', 'max-age=63072000; includeSubDomains; preload')
      headers.set('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'; upgrade-insecure-requests")
      headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()')
      headers.set('Referrer-Policy', 'strict-origin-when-cross-origin')
      headers.set('X-Content-Type-Options', 'nosniff')
      headers.set('X-Frame-Options', 'SAMEORIGIN')
      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers,
      })
    }

    const response = await env.ASSETS.fetch(request)

    if (response.status !== 404) return secure(response)

    const url = new URL(request.url)
    if (url.pathname.includes('.')) return secure(response)

    const staticPageUrl = new URL(request.url)
    staticPageUrl.pathname = `${staticPageUrl.pathname.replace(/\/$/, '')}/index.html`
    const staticPageResponse = await env.ASSETS.fetch(new Request(staticPageUrl, request))

    if (staticPageResponse.status !== 404) return secure(staticPageResponse)

    url.pathname = '/'
    return secure(await env.ASSETS.fetch(new Request(url, request)))
  },
}
