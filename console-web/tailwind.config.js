/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        // Match the SIGDEP-3 hub legacy: Roboto for body, system stack as fallback.
        sans: ['Roboto', 'system-ui', '-apple-system', 'Segoe UI', 'Helvetica', 'Arial', 'sans-serif'],
      },
      colors: {
        // SIGDEP brand teal (matches the green/teal logos already used on the
        // legacy hub) plus standard "PNLS official" blue for headers/links.
        sigdep: {
          50:  '#e7f5f3',
          100: '#c4e6e1',
          200: '#9ed6cd',
          300: '#76c5b8',
          400: '#56b9aa',
          500: '#009d8e', // primary
          600: '#008a7c',
          700: '#007568',
          800: '#005f54',
          900: '#003e36',
        },
        ink: {
          DEFAULT: '#1f2937', // slate-800
          muted:   '#6b7280', // slate-500
          subtle:  '#9ca3af', // slate-400
        },
      },
      boxShadow: {
        card: '0 1px 2px 0 rgb(0 0 0 / 0.05), 0 1px 3px 0 rgb(0 0 0 / 0.05)',
      },
    },
  },
  plugins: [],
};
