import type { Config } from 'tailwindcss';
import tailwindcssAnimate from 'tailwindcss-animate';

export default {
  // Class-based dark mode (UI-SPEC §Design System).
  // Toggle UI is deferred to Phase 9; CSS variables for both `:root` and `.dark`
  // ship in `src/index.css` so feature phases never need to retrofit.
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    container: {
      center: true,
      padding: {
        DEFAULT: '1rem'
      },
      screens: {
        '2xl': '1280px'
      }
    },
    extend: {
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
          'sans-serif'
        ]
      },
      fontSize: {
        'headline-lg': ['2rem', { lineHeight: '2.5rem', fontWeight: '400' }],
        'title-lg': ['1.375rem', { lineHeight: '1.75rem', fontWeight: '400' }],
        'title-md': ['1rem', { lineHeight: '1.5rem', fontWeight: '500' }],
        'body-lg': ['1rem', { lineHeight: '1.5rem', fontWeight: '400' }],
        'body-md': ['0.875rem', { lineHeight: '1.25rem', fontWeight: '400' }],
        'label-lg': ['0.875rem', { lineHeight: '1.25rem', fontWeight: '500' }],
        'label-sm': ['0.6875rem', { lineHeight: '1rem', fontWeight: '500' }],
      },
      colors: {
        border: 'hsl(var(--border))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))'
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))'
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))'
        },
        ring: 'hsl(var(--ring))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))'
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))'
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))'
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))'
        },
        input: 'hsl(var(--input))',
        chart: {
          '1': 'hsl(var(--chart-1))',
          '2': 'hsl(var(--chart-2))',
          '3': 'hsl(var(--chart-3))',
          '4': 'hsl(var(--chart-4))',
          '5': 'hsl(var(--chart-5))'
        },
        elevation: {
          '1': 'hsl(var(--elevation-1))',
          '2': 'hsl(var(--elevation-2))',
          '3': 'hsl(var(--elevation-3))',
        },
        'primary-container': {
          DEFAULT: 'hsl(var(--primary-container))',
          foreground: 'hsl(var(--on-primary-container))',
        },
        'secondary-container': {
          DEFAULT: 'hsl(var(--secondary-container))',
          foreground: 'hsl(var(--on-secondary-container))',
        },
        'surface-tint': 'hsl(var(--surface-tint))',
        outline: {
          DEFAULT: 'hsl(var(--outline))',
          variant: 'hsl(var(--outline-variant))',
        },
      },
      borderRadius: {
        none: '0px',
        xs: '4px',
        sm: '8px',
        md: '12px',
        lg: '16px',
        xl: '28px',
        full: '9999px',
        DEFAULT: 'var(--radius)',
      }
    }
  },
  plugins: [tailwindcssAnimate],
} satisfies Config;
