import { expect, test } from '@playwright/test'

const endpoint = {
  id: '10000000-0000-4000-8000-000000000001',
  name: 'Orders',
  url: 'https://example.com/webhooks',
  enabled: true,
  createdAt: '2026-09-18T12:00:00Z',
}

const deliveries = [
  {
    id: '20000000-0000-4000-8000-000000000001',
    eventId: '30000000-0000-4000-8000-000000000001',
    endpointId: endpoint.id,
    status: 'RETRY_SCHEDULED',
    attemptCount: 2,
    lastError: 'Connection timed out',
    nextAttemptAt: '2026-09-18T12:10:00Z',
    createdAt: '2026-09-18T12:02:00Z',
  },
  {
    id: '20000000-0000-4000-8000-000000000002',
    eventId: '30000000-0000-4000-8000-000000000002',
    endpointId: endpoint.id,
    status: 'SUCCEEDED',
    attemptCount: 1,
    lastError: null,
    nextAttemptAt: null,
    createdAt: '2026-09-18T12:01:00Z',
  },
]

async function mockApi(page, state = {}) {
  const records = state.deliveries || [...deliveries]
  const endpoints = state.endpoints || [endpoint]
  await page.route('**/api/deliveries', (route) => route.fulfill({ json: records }))
  await page.route('**/api/endpoints', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON()
      const created = { ...body, id: '10000000-0000-4000-8000-000000000002', enabled: true, createdAt: '2026-09-18T12:03:00Z' }
      endpoints.push(created)
      return route.fulfill({ status: 201, json: created })
    }
    return route.fulfill({ json: endpoints })
  })
  await page.route('**/api/deliveries/*/attempts', (route) => route.fulfill({ json: [{
    id: '40000000-0000-4000-8000-000000000001',
    deliveryId: deliveries[0].id,
    attemptNumber: 2,
    status: 'FAILED',
    httpStatus: 503,
    errorMessage: 'Service unavailable',
    startedAt: '2026-09-18T12:03:00Z',
    finishedAt: '2026-09-18T12:03:01Z',
  }] }))
  await page.route('**/api/events', (route) => {
    const body = route.request().postDataJSON()
    const event = { ...body, id: '30000000-0000-4000-8000-000000000003', deliveryCount: endpoints.length, createdAt: '2026-09-18T12:04:00Z' }
    records.unshift({ ...deliveries[1], id: '20000000-0000-4000-8000-000000000003', eventId: event.id, createdAt: event.createdAt })
    return route.fulfill({ status: 201, json: event })
  })
}

test('filters deliveries and shows attempt history', async ({ page }, testInfo) => {
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByRole('table')).toContainText('Connection timed out')
  await page.screenshot({ path: testInfo.outputPath('deliveries-desktop.png'), fullPage: true })

  await page.getByRole('button', { name: 'View delivery 20000000' }).first().click()
  await expect(page.getByRole('dialog')).toContainText('Attempt 2')
  await expect(page.getByRole('dialog')).toContainText('HTTP 503')
  await page.getByRole('button', { name: 'Close dialog' }).click()

  await page.getByRole('combobox', { name: 'Filter by status' }).selectOption('SUCCEEDED')
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1)
  await page.getByRole('textbox', { name: 'Search records' }).fill('missing')
  await expect(page.getByRole('heading', { name: 'No matching records' })).toBeVisible()
  await page.getByRole('button', { name: 'Clear filters' }).click()
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(2)
})

test('creates endpoints and events through the API', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: /Endpoints/ }).click()
  await expect(page.getByRole('table')).toContainText('Orders')
  await page.getByRole('button', { name: 'Add endpoint' }).first().click()
  await page.getByRole('dialog').getByRole('textbox', { name: 'Name' }).fill('Billing')
  await page.getByRole('dialog').getByRole('textbox', { name: 'Endpoint URL' }).fill('https://example.com/billing')
  await page.getByRole('dialog').getByRole('button', { name: 'Add endpoint' }).click()
  await expect(page.getByRole('table')).toContainText('Billing')

  await page.getByRole('button', { name: /Deliveries/ }).click()
  await page.getByRole('button', { name: 'Create event' }).first().click()
  await page.getByRole('dialog').getByRole('textbox', { name: 'Event type' }).fill('invoice.created')
  await page.getByRole('dialog').getByRole('button', { name: 'Create event' }).click()
  await expect(page.getByRole('status')).toContainText('2 deliveries created')
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(3)
})

test('filters endpoints and paginates larger delivery lists', async ({ page }) => {
  const manyDeliveries = Array.from({ length: 14 }, (_, index) => ({
    ...deliveries[1],
    id: `20000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
    status: 'PENDING',
    createdAt: new Date(Date.UTC(2026, 8, 18, 12, index)).toISOString(),
  }))
  await mockApi(page, { deliveries: manyDeliveries, endpoints: [endpoint, {
    ...endpoint, id: '10000000-0000-4000-8000-000000000003', name: 'Paused', enabled: false,
  }] })
  await page.goto('/')
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(12)
  await page.getByRole('button', { name: 'Next page' }).click()
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(2)

  await page.getByRole('button', { name: /Endpoints/ }).click()
  await page.getByRole('combobox', { name: 'Filter endpoints by status' }).selectOption('disabled')
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1)
  await expect(page.getByRole('table')).toContainText('Paused')
  await page.getByRole('textbox', { name: 'Search records' }).fill('Orders')
  await expect(page.getByRole('heading', { name: 'No matching records' })).toBeVisible()
})

test('mobile layout stays within viewport', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Deliveries' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('deliveries-mobile.png'), fullPage: true })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: /Endpoints/ }).click()
  await expect(page.getByRole('table')).toContainText('Orders')
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('endpoints-mobile.png'), fullPage: true })
  await page.setViewportSize({ width: 320, height: 700 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: /Deliveries/ }).click()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('deliveries-narrow-mobile.png'), fullPage: true })
  await page.getByRole('button', { name: 'Create event' }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('shows a recoverable error when the API is unavailable', async ({ page }) => {
  await page.route('**/api/deliveries', (route) => route.fulfill({ status: 503, json: { message: 'API unavailable' } }))
  await page.route('**/api/endpoints', (route) => route.fulfill({ status: 503, json: { message: 'API unavailable' } }))
  await page.goto('/')
  await expect(page.getByRole('alert')).toContainText('API unavailable')
  await expect(page.getByRole('heading', { name: 'Data unavailable' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Retry', exact: true })).toBeVisible()
})
