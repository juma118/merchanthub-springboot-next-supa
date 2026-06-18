// Captures dashboard screenshots into ../../docs/screenshots/.
// Usage: BASE=http://localhost:3000 node shoot.js
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE = process.env.BASE || 'http://localhost:3000';
const EMAIL = process.env.EMAIL || 'demo@merchanthub.dev';
const OUT = path.resolve(__dirname, '../../docs/screenshots');

fs.mkdirSync(OUT, { recursive: true });

async function shoot(page, name, { full = false } = {}) {
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: full });
  console.log('saved', path.relative(process.cwd(), file));
}

async function settle(page, ms = 1200) {
  try { await page.waitForLoadState('networkidle', { timeout: 8000 }); } catch {}
  await page.waitForTimeout(ms);
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
  });
  const page = await ctx.newPage();

  // 1) Login page (pre-auth)
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await settle(page);
  await shoot(page, '01-login');

  // Perform dev login
  try {
    const email = page.locator('input[type="email"], input[name="email"], input').first();
    await email.fill(EMAIL);
  } catch (e) { console.log('email fill skipped:', e.message); }
  try {
    await page.getByRole('button', { name: /log\s*in|sign\s*in/i }).first().click();
  } catch (e) {
    try { await page.locator('button[type="submit"], form button').first().click(); }
    catch (e2) { console.log('login click skipped:', e2.message); }
  }
  try { await page.waitForURL(/dashboard/i, { timeout: 12000 }); } catch {}
  await settle(page, 1800);
  await shoot(page, '02-dashboard');

  const routes = [
    ['products', '03-products'],
    ['inventory', '04-inventory'],
    ['orders', '05-orders'],
    ['alerts', '06-alerts'],
    ['sync', '07-sync'],
  ];
  for (const [route, name] of routes) {
    try {
      await page.goto(`${BASE}/${route}`, { waitUntil: 'domcontentloaded' });
      await settle(page, 1400);
      await shoot(page, name);
    } catch (e) { console.log(`route ${route} failed:`, e.message); }
  }

  // 8) Order detail drawer (popup) — click first order row
  try {
    await page.goto(`${BASE}/orders`, { waitUntil: 'domcontentloaded' });
    await settle(page, 1400);
    await page.locator('tbody tr').first().click();
    await page.waitForTimeout(900);
    await shoot(page, '08-order-detail');
  } catch (e) { console.log('order drawer failed:', e.message); }

  // 9) Product create modal (popup)
  try {
    await page.goto(`${BASE}/products`, { waitUntil: 'domcontentloaded' });
    await settle(page, 1400);
    await page.getByRole('button', { name: /new|add|create/i }).first().click();
    await page.waitForTimeout(900);
    await shoot(page, '09-product-modal');
  } catch (e) { console.log('product modal failed:', e.message); }

  await browser.close();
  console.log('done');
})().catch((e) => { console.error(e); process.exit(1); });
