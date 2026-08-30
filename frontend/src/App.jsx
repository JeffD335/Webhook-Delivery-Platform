import { useEffect, useState } from 'react'
import './App.css'

function statusClassName(status) {
  const normalized = String(status ?? 'unknown').toLowerCase().replace(/_/g, '-')
  return `status-badge ${normalized}`
}

function shortId(id) {
  if (!id) {
    return '-'
  }

  return id.slice(0, 8)
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return new Date(value).toLocaleString()
}

function App() {
  // deliveries
  const [deliveries, setDeliveries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  // attempts
  const [selectedDelivery, setSelectedDelivery] = useState(null)
  const [attempts, setAttempts] = useState([])
  const [attemptsLoading, setAttemptsLoading] = useState(false)
  const [attemptsError, setAttemptsError] = useState(null)
  // event
  const [eventType, setEventType] = useState('order.created')
  const [payloadText, setPayloadText] = useState('{"orderId":"order_dashboard_1"}')
  const [eventSubmitLoading, setEventSubmitLoading] = useState(false)
  const [eventSubmitError, setEventSubmitError] = useState(null)
  const [lastCreatedEvent, setLastCreatedEvent] = useState(null)

  
  
  async function createEvent(event) {
    event.preventDefault()
    setEventSubmitError(null)
    setEventSubmitLoading(true)
    setLastCreatedEvent(null)
    try {

      const payload = JSON.parse(payloadText)
      const response = await fetch('/api/events',{
        method: 'POST',
        headers: {
          'Content-Type':
          'application/json',
        },
        body: JSON.stringify({
          type: eventType,
          payload: payload,
        }),
      })

      if (!response.ok) {
       throw new Error(`API returned ${response.status}`)
      }

      const data = await response.json()
      setLastCreatedEvent(data)
      await loadDeliveries()

    } catch (err) {
      setEventSubmitError(err.message)
    } finally {
      setEventSubmitLoading(false)
    }
  }

  async function loadAttempts(delivery) {
    setSelectedDelivery(delivery)
    setAttemptsLoading(true)
    setAttemptsError(null)
    setAttempts([])

    try {
      const response = await fetch(`/api/deliveries/${delivery.id}/attempts`)

      if (!response.ok) {
        throw new Error(`API returned ${response.status}`)
      }

      const data = await response.json()
      setAttempts(data)
    } catch (err) {
      setAttemptsError(err.message)
    } finally {
      setAttemptsLoading(false)
    }
  }

  async function loadDeliveries() {
    setLoading(true)
    setError(null)

    try {
      const response = await fetch('/api/deliveries')

      if (!response.ok) {
        throw new Error(`API returned ${response.status}`)
      }

      const data = await response.json()
      setDeliveries(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDeliveries()
  }, [])

  const totalCount = deliveries.length
  const succeededCount = deliveries.filter((delivery) => delivery.status === 'SUCCEEDED').length
  const retryCount = deliveries.filter((delivery) => delivery.status === 'RETRY_SCHEDULED').length
  const failedCount = deliveries.filter((delivery) => delivery.status === 'FAILED').length

  return (
    <main className="app-shell">
      <header className="page-header">
        <div>
          <h1>Webhook Delivery Dashboard</h1>
          <p>Operator view for delivery state, retries, claims, and attempts.</p>
        </div>

        <button type="button" className="primary-action" onClick={loadDeliveries}>
          Refresh
        </button>
      </header>

      <section className="summary-grid" aria-label="Delivery summary">
        <div className="summary-card">
          <span>Total deliveries</span>
          <strong>{totalCount}</strong>
        </div>
        <div className="summary-card success">
          <span>Succeeded</span>
          <strong>{succeededCount}</strong>
        </div>
        <div className="summary-card warning">
          <span>Retry scheduled</span>
          <strong>{retryCount}</strong>
        </div>
        <div className="summary-card danger">
          <span>Dead-lettered</span>
          <strong>{failedCount}</strong>
        </div>
      </section>

      <section className="event-panel panel">
        <div className="panel-header">
          <div>
            <h2>Create event</h2>
            <p>Send a new business event into the delivery pipeline.</p>
          </div>
        </div>

        <form className="event-form" onSubmit={createEvent}>
          <label>
            Type
            <input value={eventType} onChange={(event) => setEventType(event.target.value)}/>
          </label>
          <label>
            Payload JSON
            <textarea rows="4" value={payloadText} onChange={(event) => setPayloadText(event.target.value)}/>
          </label>
            <button type="submit" disabled={eventSubmitLoading}> {eventSubmitLoading ? 'Creating...' : 'Create event'}</button>
        </form>

  {eventSubmitError && (
      <div className="state-message error">
        <strong>Failed to create event</strong>
          <span>{eventSubmitError}</span>
      </div>
  )}

  {lastCreatedEvent && (
    <div className="state-message success-message">
      <strong>Event created</strong>
      <span>
        {shortId(lastCreatedEvent.id)} created {lastCreatedEvent.deliveryCount} delivery.
      </span>
    </div>
  )}
      </section>

      <section className="dashboard-grid">
        <section className="panel deliveries-panel">
          <div className="panel-header">
            <div>
              <h2>Recent deliveries</h2>
              <p>Newest deliveries from the backend delivery table.</p>
            </div>
          </div>

          {loading && <p className="muted">Loading deliveries...</p>}

          {error && (
            <div className="state-message error">
              <strong>Failed to load deliveries</strong>
              <span>{error}</span>
            </div>
          )}

          {!loading && !error && deliveries.length === 0 && (
            <div className="state-message empty">
              <strong>No deliveries yet</strong>
              <span>Create an event first, then refresh this dashboard.</span>
            </div>
          )}

          {!loading && !error && deliveries.length > 0 && (
            <div className="table-wrap">
              <table className="delivery-table">
                <thead>
                  <tr>
                    <th>Delivery</th>
                    <th>Status</th>
                    <th>Attempts</th>
                    <th>Last error</th>
                    <th>Claimed by</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {deliveries.map((delivery) => (
                    <tr
                      key={delivery.id}
                      className={selectedDelivery?.id === delivery.id ? 'selected-row' : ''}
                      onClick={() => loadAttempts(delivery)}
                    >
                      <td>
                        <div className="id-cell">
                          <span title={delivery.id}>{shortId(delivery.id)}</span>
                          <small title={delivery.eventId}>event {shortId(delivery.eventId)}</small>
                        </div>
                      </td>
                      <td>
                        <span className={statusClassName(delivery.status)}>
                          {delivery.status}
                        </span>
                      </td>
                      <td>{delivery.attemptCount}</td>
                      <td className="error-cell">{delivery.lastError ?? '-'}</td>
                      <td>{delivery.claimedBy ?? '-'}</td>
                      <td>{formatDateTime(delivery.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="panel attempts-panel">
          <div className="panel-header">
            <div>
              <h2>Attempts</h2>
              <p>
                {selectedDelivery
                  ? `Delivery ${shortId(selectedDelivery.id)}`
                  : 'Select a delivery to inspect attempts.'}
              </p>
            </div>
          </div>

          {!selectedDelivery && (
            <div className="state-message empty">
              <strong>No delivery selected</strong>
              <span>Click a delivery row to load its attempts.</span>
            </div>
          )}

          {selectedDelivery && attemptsLoading && (
            <p className="muted">Loading attempts...</p>
          )}

          {selectedDelivery && attemptsError && (
            <div className="state-message error">
              <strong>Failed to load attempts</strong>
              <span>{attemptsError}</span>
            </div>
          )}

          {selectedDelivery && !attemptsLoading && !attemptsError && attempts.length === 0 && (
            <div className="state-message empty">
              <strong>No attempts yet</strong>
              <span>This delivery has not been processed by a worker.</span>
            </div>
          )}

          {selectedDelivery && !attemptsLoading && !attemptsError && attempts.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Status</th>
                    <th>HTTP</th>
                    <th>Error</th>
                  </tr>
                </thead>
                <tbody>
                  {attempts.map((attempt) => (
                    <tr key={attempt.id}>
                      <td>{attempt.attemptNumber}</td>
                      <td>
                        <span className={statusClassName(attempt.status)}>
                          {attempt.status}
                        </span>
                      </td>
                      <td>{attempt.httpStatus ?? '-'}</td>
                      <td className="error-cell">{attempt.errorMessage ?? '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </section>
    </main>
  )
}

export default App
