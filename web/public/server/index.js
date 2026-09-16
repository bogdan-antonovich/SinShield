export default {
  async fetch(request, env) {
    const response = await env.ASSETS.fetch(request)

    if (response.status !== 404) return response

    const url = new URL(request.url)
    if (url.pathname.includes('.')) return response

    const staticPageUrl = new URL(request.url)
    staticPageUrl.pathname = `${staticPageUrl.pathname.replace(/\/$/, '')}/index.html`
    const staticPageResponse = await env.ASSETS.fetch(new Request(staticPageUrl, request))

    if (staticPageResponse.status !== 404) return staticPageResponse

    url.pathname = '/'
    return env.ASSETS.fetch(new Request(url, request))
  },
}
