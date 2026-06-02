import { defineConfig } from 'cypress';

// Dedicated config for recording the README demo screencast.
// Run with: npx cypress run --browser chrome --config-file cypress/demo.config.ts
// It records video of the demo spec (which lives outside cypress/e2e so CI never runs it).
export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:8280',
    specPattern: 'cypress/demo/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    setupNodeEvents(on) {
      // Enlarge the headless Chrome window so the 1700x1280 viewport renders ~1:1 in the
      // run-mode video (pane >= viewport) instead of being downscaled to ~46% — this is what
      // makes the recording high-resolution / crisp.
      on('before:browser:launch', (browser, launchOptions) => {
        if (browser.family === 'chromium') {
          launchOptions.args.push('--window-size=2300,1450'); // pane ~1850x1410 >= 1700x1280
          launchOptions.args.push('--hide-scrollbars');
        }
        return launchOptions;
      });
    },
    defaultCommandTimeout: 20000, // the demo paints ~1200 strokes in batched native-event loops
    viewportWidth: 1700,
    viewportHeight: 1280,
    video: true,
    videosFolder: 'demo-out/raw',
    screenshotOnRunFailure: false,
  },
});
