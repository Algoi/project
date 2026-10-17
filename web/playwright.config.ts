import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    actionTimeout: 10000,
    navigationTimeout: 15000,
    baseURL: process.env.WEB_BASE_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    launchOptions: { executablePath: process.env.CHROME_PATH ?? '/usr/bin/google-chrome' },
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
})
