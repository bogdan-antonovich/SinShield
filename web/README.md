# SinShield web

The Vue application follows a module-oriented source structure:

```text
src/
├── assets/                  # Fonts, icons, images, and global styles
├── common/                  # Reusable components, helpers, and directives (when needed)
├── layouts/                 # Route-shell layouts and their private components
├── middlewares/             # Router navigation guards (when needed)
├── modules/                 # Business-feature code and module route definitions
├── plugins/                 # Vue plugin setup (when needed)
├── router/                  # Root router creation and route aggregation
├── services/                # API and browser-storage integrations (when needed)
├── static/                  # Application fixture data (when needed)
├── store/                   # Root state setup (when needed)
├── views/                   # Route entry points
├── App.vue                  # Root application component
└── main.ts                  # Application bootstrap
```

Only directories with current implementation files are committed. Feature-specific
components, types, helpers, tests, and routes belong under their corresponding
`modules/<feature>` directory. A component moves to `common` only once it is shared
across modules.

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we replace the `tsc` CLI with `vue-tsc` for type checking. In editors, we need [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) to make the TypeScript language service aware of `.vue` types.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Type-Check, Compile and Minify for Production

```sh
npm run build
```

### Run Unit Tests with [Vitest](https://vitest.dev/)

```sh
npm run test:unit
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```

## CI/CD

GitHub Actions runs linting, type checking, unit tests, and a production build for
pull requests and pushes to `master`. After a successful `master` build, the CD
workflow publishes an immutable Docker image and deploys it to a Docker Swarm
VPS.

Configure these GitHub repository settings before enabling production deploys:

- Variables: `DOCKERHUB_USERNAME`, `WEB_PORT`
- Secrets: `DOCKERHUB_TOKEN`, `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`

The VPS must have Docker Swarm initialized, and `WEB_PORT` must be available on
the target node. The workflow creates and updates the `sinshield-web` stack.
