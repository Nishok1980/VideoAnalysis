/* CI browser acceptance against the running Java application. */
const { chromium } = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1400, height: 1100 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://127.0.0.1:8080');
    await page.waitForFunction(() =>
      document.getElementById('notice').textContent.includes('Setup ready'));
    await page.locator('#file').setInputFiles(process.env.VIDEO_PEOPLE_CLIP);
    await page.locator('#uploadButton').click();
    await page.waitForFunction(() =>
      document.getElementById('notice').textContent.includes('Recording ready'));
    await page.getByText('Enter area coordinates instead').click();
    await page.locator('#areaX').fill('0.1');
    await page.locator('#areaWidth').fill('0.8');
    await page.locator('#applyArea').click();
    assert.match(await page.locator('#areaSummary').textContent(), /80%/);
    await page.locator('#wholeFrame').click();
    await page.locator('#recordingStart').fill('2026-10-12T23:00:00');
    await page.locator('#schedule').check();
    await page.locator('#presence').fill('1');
    await page.locator('#linger').fill('2');
    await page.locator('#analyseButton').click();
    await page.waitForFunction(() =>
      document.getElementById('jobStatus').textContent.startsWith('COMPLETED'),
      null, { timeout: 120000 });
    assert.ok(await page.locator('.event').count() > 0);
    await page.locator('.event select').first().selectOption('UNCERTAIN');
    await page.waitForFunction(() =>
      document.getElementById('notice').textContent.startsWith('Review saved'));
    await page.locator('.event button').first().click();
    await page.waitForFunction(() =>
      document.getElementById('player').currentTime > 0.1);
    const downloadWait = page.waitForEvent('download');
    await page.locator('#export').click();
    const download = await downloadWait;
    const report = JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
    assert.equal(report.analysis.events[0].review, 'UNCERTAIN');
    assert.match(report.analysis.rules.recordingStart, /\+05:30$/);
    await page.screenshot({ path: 'target/ui-desktop.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() =>
      document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: 'target/ui-narrow.png', fullPage: true });
    assert.deepEqual(errors, []);
    console.log('PASS: browser upload, area selection, scheduled analysis, review, playback, JSON and narrow layout');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
