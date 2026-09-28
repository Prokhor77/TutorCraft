import type { Config } from 'tailwindcss';

/** All visual values come from CSS variables in src/styles/tokens.css (UX-10). Re-skin there, not here. */
const tokenColor = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

const config: Config = {
  darkMode: ['class', '[data-theme="dark"]'],
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        background: tokenColor('background'),
        surface: tokenColor('surface'),
        'surface-muted': tokenColor('surface-muted'),
        border: tokenColor('border'),
        text: tokenColor('text'),
        'text-muted': tokenColor('text-muted'),
        primary: tokenColor('primary'),
        'primary-foreground': tokenColor('primary-foreground'),
        'primary-soft': tokenColor('primary-soft'),
        success: tokenColor('success'),
        'success-soft': tokenColor('success-soft'),
        warning: tokenColor('warning'),
        'warning-soft': tokenColor('warning-soft'),
        danger: tokenColor('danger'),
        'danger-foreground': tokenColor('danger-foreground'),
        'danger-soft': tokenColor('danger-soft'),
        info: tokenColor('info'),
        'info-soft': tokenColor('info-soft'),
        'focus-ring': tokenColor('focus-ring'),
        overlay: tokenColor('overlay'),
      },
      borderRadius: {
        xs: 'var(--radius-xs)',
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
        full: 'var(--radius-full)',
      },
      boxShadow: {
        sm: 'var(--shadow-sm)',
        md: 'var(--shadow-md)',
        lg: 'var(--shadow-lg)',
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-mono)', 'ui-monospace', 'monospace'],
      },
      fontSize: {
        xs: ['var(--text-xs)', { lineHeight: 'var(--leading-xs)' }],
        sm: ['var(--text-sm)', { lineHeight: 'var(--leading-sm)' }],
        base: ['var(--text-base)', { lineHeight: 'var(--leading-base)' }],
        lg: ['var(--text-lg)', { lineHeight: 'var(--leading-lg)' }],
        xl: ['var(--text-xl)', { lineHeight: 'var(--leading-xl)' }],
        '2xl': ['var(--text-2xl)', { lineHeight: 'var(--leading-2xl)' }],
        '3xl': ['var(--text-3xl)', { lineHeight: 'var(--leading-3xl)' }],
        '4xl': ['var(--text-4xl)', { lineHeight: 'var(--leading-4xl)' }],
      },
      spacing: {
        'page-x': 'var(--space-page-x)',
        'page-y': 'var(--space-page-y)',
        sidebar: 'var(--size-sidebar)',
        header: 'var(--size-header)',
        'bottom-nav': 'var(--size-bottom-nav)',
      },
      maxWidth: {
        content: 'var(--size-content)',
        prose: 'var(--size-prose)',
      },
      transitionDuration: {
        fast: 'var(--motion-fast)',
        base: 'var(--motion-base)',
      },
      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'slide-up': {
          from: { transform: 'translateY(8px)', opacity: '0' },
          to: { transform: 'none', opacity: '1' },
        },
        'slide-in-right': { from: { transform: 'translateX(100%)' }, to: { transform: 'none' } },
        shimmer: {
          '0%': { opacity: '0.55' },
          '50%': { opacity: '1' },
          '100%': { opacity: '0.55' },
        },
      },
      animation: {
        'fade-in': 'fade-in var(--motion-base) ease-out',
        'slide-up': 'slide-up var(--motion-base) ease-out',
        'slide-in-right': 'slide-in-right var(--motion-base) ease-out',
        shimmer: 'shimmer 1.4s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};

export default config;
