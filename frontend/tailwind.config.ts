import type { Config } from 'tailwindcss';

export default {
  // Class-based dark mode (UI-SPEC §Design System).
  // Toggle UI is deferred to Phase 9; CSS variables for both `:root` and `.dark`
  // ship in `src/index.css` so feature phases never need to retrofit.
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    container: {
      center: true,
      padding: { DEFAULT: '1rem' },
      screens: { '2xl': '1280px' },
    },
    extend: {
      // System font stack — no Google Fonts in v1 (UI-SPEC §Design System "Font loading strategy").
      fontFamily: {
        sans: [
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
      // shadcn slate palette via CSS vars (UI-SPEC §Color).
      // Values defined in src/index.css for both `:root` (light) and `.dark`.
      colors: {
        border: 'hsl(var(--border))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        ring: 'hsl(var(--ring))',
      },
    },
  },
  plugins: [],
} satisfies Config;
