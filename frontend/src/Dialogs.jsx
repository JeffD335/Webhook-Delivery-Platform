import { useEffect, useState } from 'react'
import { Check, CircleAlert, Clock3, LoaderCircle, Plus, RefreshCw, Send, Server, X } from 'lucide-react'
import { CopyButton, Dialog, IconButton, Status } from './ui'
import { dateTime, request } from './data'

export function CreateDialog({ kind, endpointCount, onClose, onCreated }) {
  const isEvent = kind === 'event'
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [type, setType] = useState('order.created')
  const [payload, setPayload] = useState('{\n  "orderId": "order_001"\n}')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault()
    setError('')
    let body
    try {
      if (isEvent) {
        let parsed
        try { parsed = JSON.parse(payload) } catch { throw new Error('Payload must be valid JSON.') }
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('Payload must be a JSON object.')
        body = { type: type.trim(), payload: parsed }
      } else {
        const parsed = new URL(url.trim())
        if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Use an HTTP or HTTPS endpoint URL.')
        body = { name: name.trim(), url: url.trim() }
      }
      setPending(true)
      const result = await request(isEvent ? '/api/events' : '/api/endpoints', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      onCreated(result)
    } catch (err) { setError(err.message); setPending(false) }
  }
  return <Dialog title={isEvent ? 'Create event' : 'Add endpoint'} onClose={() => !pending && onClose()}>
    <form onSubmit={submit} className="create-form">
      {isEvent ? <>
        <label>Event type<input autoFocus required value={type} onChange={(e) => setType(e.target.value)} placeholder="order.created" disabled={pending} /></label>
        <label>Payload <span className="field-meta">JSON</span><textarea className="code-input" rows={9} spellCheck="false" value={payload} onChange={(e) => setPayload(e.target.value)} disabled={pending} /></label>
        <div className="form-note"><Server size={16} /><span>{endpointCount == null ? 'Endpoint count unavailable.' : `${endpointCount} registered endpoint${endpointCount === 1 ? '' : 's'}`}</span></div>
        {endpointCount === 0 && <div className="notice amber">No endpoints registered. This event will create no deliveries.</div>}
      </> : <>
        <label>Name<input autoFocus required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} placeholder="Order service" disabled={pending} /></label>
        <label>Endpoint URL<input type="url" required value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/webhooks" disabled={pending} /></label>
      </>}
      {error && <div className="notice red" role="alert"><CircleAlert size={16} />{error}</div>}
      <footer className="form-actions"><button type="button" className="button" onClick={onClose} disabled={pending}>Cancel</button><button type="submit" className="button primary" disabled={pending}>{pending ? <LoaderCircle size={16} className="spin" /> : isEvent ? <Send size={16} /> : <Plus size={16} />}{pending ? 'Saving...' : isEvent ? 'Create event' : 'Add endpoint'}</button></footer>
    </form>
  </Dialog>
}

export function DeliveryDetail({ delivery, endpoint, revision, onClose }) {
  const [state, setState] = useState({ items: [], loading: true, error: '' })
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    request(`/api/deliveries/${delivery.id}/attempts`, { signal: controller.signal })
      .then((items) => { if (!Array.isArray(items)) throw new Error('Invalid attempt response.'); setState({ items, loading: false, error: '' }) })
      .catch((err) => { if (err.name !== 'AbortError') setState((old) => ({ ...old, loading: false, error: err.message })) })
    return () => controller.abort()
  }, [delivery.id, revision, retry])
  const attempts = [...state.items].sort((a, b) => b.attemptNumber - a.attemptNumber)
  return <Dialog drawer title="Delivery details" onClose={onClose}>
    <div className="detail-body">
      <div className="detail-status"><Status value={delivery.status} /><span className="muted">{delivery.attemptCount} attempts</span></div>
      <dl className="metadata">
        <div><dt>Delivery ID</dt><dd><code>{delivery.id}</code><CopyButton value={delivery.id} /></dd></div>
        <div><dt>Event ID</dt><dd><code>{delivery.eventId}</code><CopyButton value={delivery.eventId} /></dd></div>
        <div><dt>Endpoint</dt><dd>{endpoint?.name || delivery.endpointId}</dd></div>
        {endpoint?.url && <div><dt>Destination</dt><dd className="mono">{endpoint.url}</dd></div>}
        <div><dt>Created</dt><dd>{dateTime(delivery.createdAt)}</dd></div>
        {delivery.nextAttemptAt && <div><dt>Next attempt</dt><dd>{dateTime(delivery.nextAttemptAt)}</dd></div>}
        {delivery.claimedBy && <div><dt>Claimed by</dt><dd>{delivery.claimedBy}</dd></div>}
      </dl>
      {delivery.lastError && <div className="notice red"><CircleAlert size={16} /><span>{delivery.lastError}</span></div>}
      <div className="section-heading"><h3>Attempt history <span className="count">{state.items.length}</span></h3><IconButton label="Refresh attempts" onClick={() => setRetry((n) => n + 1)}><RefreshCw size={15} /></IconButton></div>
      {state.error && <div className="notice red" role="alert">{state.error}</div>}
      {state.loading ? <div className="empty compact"><LoaderCircle className="spin" size={22} />Loading attempts...</div> : attempts.length === 0 ? <div className="empty compact"><Clock3 size={24} /><strong>No attempts recorded</strong></div> : <ol className="attempt-list">
        {attempts.map((attempt) => {
          const duration = attempt.finishedAt && attempt.startedAt ? Math.max(0, new Date(attempt.finishedAt) - new Date(attempt.startedAt)) : null
          return <li key={attempt.id}>
            <span className={`attempt-marker ${attempt.status === 'SUCCEEDED' ? 'green' : attempt.status === 'FAILED' ? 'red' : 'neutral'}`}>{attempt.status === 'SUCCEEDED' ? <Check size={13} /> : attempt.status === 'FAILED' ? <X size={13} /> : <Clock3 size={13} />}</span>
            <div className="attempt-content"><div className="attempt-top"><strong>Attempt {attempt.attemptNumber}</strong><Status value={attempt.status} /></div><div className="attempt-meta"><span>{attempt.httpStatus ? `HTTP ${attempt.httpStatus}` : 'No HTTP response'}</span><span>{duration == null ? '\u2014' : `${duration} ms`}</span></div><time>{dateTime(attempt.startedAt)}</time>{attempt.errorMessage && <p className="attempt-error">{attempt.errorMessage}</p>}</div>
          </li>
        })}
      </ol>}
    </div>
  </Dialog>
}
