import { CheckCircle2, Clock3, LoaderCircle, RefreshCw, XCircle } from 'lucide-react'

export const STATUS = {
  PENDING: { label: 'Pending', tone: 'neutral', icon: Clock3 },
  IN_PROGRESS: { label: 'In progress', tone: 'blue', icon: LoaderCircle },
  RETRY_SCHEDULED: { label: 'Retry scheduled', tone: 'amber', icon: RefreshCw },
  SUCCEEDED: { label: 'Succeeded', tone: 'green', icon: CheckCircle2 },
  FAILED: { label: 'Failed', tone: 'red', icon: XCircle },
  STARTED: { label: 'Started', tone: 'blue', icon: LoaderCircle },
}

export const shortId = (id) => id?.slice(0, 8) || '\u2014'
export const dateTime = (value) => value ? new Date(value).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '\u2014'

export async function request(path, options = {}) {
  const response = await fetch(path, options)
  const body = await response.text()
  let data
  try { data = body ? JSON.parse(body) : null } catch { /* Proxy errors may return plain text. */ }
  if (!response.ok) throw new Error(data?.message || `Request failed (${response.status}). Check that the backend is running.`)
  if (data == null) throw new Error('The server returned an empty or invalid response.')
  return data
}
