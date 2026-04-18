/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          dark: '#1E3A5F',
          mid: '#2E6DA4',
          accent: '#4A90D9',
        },
      },
    },
  },
  plugins: [],
};
