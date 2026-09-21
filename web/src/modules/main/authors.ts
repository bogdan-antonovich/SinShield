export interface AuthorProfile {
  name: string
  role: string
  description: string
  path: string
  image: string
  imageAlt: string
}

export const sinShieldEditorial: AuthorProfile = {
  name: 'SinShield Editorial',
  role: 'Author and reviewer',
  description:
    'The SinShield editorial function researches, reviews, updates, and corrects practical guides about adult-content protection, platform controls, and digital habits.',
  path: '/authors/sinshield-editorial',
  image: '/sinshield-thumbnail.png',
  imageAlt: 'SinShield shield mark',
}

export const authors: AuthorProfile[] = [sinShieldEditorial]
