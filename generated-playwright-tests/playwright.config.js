const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
  testDir: '.',
  timeout: 30000,
  reporter: [['html', { outputFolder: '../playwright-report', open: 'never' }], ['list']],
  use: {
    headless: true,
    screenshot: 'only-on-failure',
  },
});
