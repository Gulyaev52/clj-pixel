// Support file for E2E tests — loaded automatically before every spec.
import './commands';
import 'cypress-diff';
import "cypress-real-events";

Cypress.on('uncaught:exception', (err) => {
  if (err.message.includes('ResizeObserver loop')) return false;
});