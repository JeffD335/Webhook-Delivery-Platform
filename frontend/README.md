# Webhook Delivery Platform frontend

React/Vite operator UI for the local webhook delivery API.

## Run locally

Start the Spring Boot API on `http://localhost:8080`, then from this directory:

```sh
npm install
npm run dev
```

Open the URL printed by Vite. The development server proxies `/api` to port 8080. Without the API, the UI displays a connection error rather than sample data.

## Current UI

- Delivery list with status filters, search, pagination, manual refresh, and optional 10-second auto-refresh.
- Delivery details with attempt history and error information.
- Endpoint list with search, enabled status, and status filtering.
- Create an endpoint or ingest an event through the API.

The enabled status is currently read-only here. The backend's event planner still selects all endpoints, so offering an enable/disable action would imply a delivery behavior the backend does not yet enforce.

## Checks

```sh
npm run lint
npm run build
npx playwright install chromium
npm run test:e2e
```

The browser tests use mocked API responses and do not create database records.
