import { useEffect, useState } from 'react'
import {
  Activity, ArrowDown, CheckCircle2, ChevronLeft, ChevronRight, CircleAlert,
  Clock3, Inbox, LoaderCircle, Plus, RefreshCw, Search, Send, Server, Webhook,
  X, XCircle,
} from 'lucide-react'
import { CopyButton, IconButton, Status } from './ui'
import { STATUS, dateTime, request, shortId } from './data'
import { CreateDialog, DeliveryDetail } from './Dialogs'
import './App.css'

const PAGE_SIZE = 12

function App() {
  const [view, setView] = useState('deliveries')
  const [data, setData] = useState({
    deliveries: [], endpoints: [], deliveryError: '', endpointError: '',
    loaded: false, endpointsLoaded: false, updatedAt: null,
  })
  const [refreshKey, setRefreshKey] = useState(0)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('all')
  const [page, setPage] = useState(1)
  const [selectedId, setSelectedId] = useState(null)
  const [modal, setModal] = useState(null)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    async function load() {
      setRefreshing(true)
      const results = await Promise.allSettled(['/api/deliveries', '/api/endpoints'].map(async (path) => {
        const items = await request(path, { signal: controller.signal })
        if (!Array.isArray(items)) throw new Error('The server returned an invalid list.')
        return items
      }))
      if (!active) return
      setData((old) => ({
        deliveries: results[0].status === 'fulfilled' ? results[0].value : old.deliveries,
        endpoints: results[1].status === 'fulfilled' ? results[1].value : old.endpoints,
        deliveryError: results[0].status === 'rejected' ? results[0].reason.message : '',
        endpointError: results[1].status === 'rejected' ? results[1].reason.message : '',
        loaded: true,
        endpointsLoaded: old.endpointsLoaded || results[1].status === 'fulfilled',
        updatedAt: results.every((item) => item.status === 'fulfilled') ? new Date() : old.updatedAt,
      }))
      setRefreshing(false)
    }
    load()
    return () => { active = false; controller.abort() }
  }, [refreshKey])

  useEffect(() => {
    if (!autoRefresh) return
    const interval = setInterval(() => {
      if (!document.hidden) setRefreshKey((n) => n + 1)
    }, 10000)
    return () => clearInterval(interval)
  }, [autoRefresh])

  const endpointsById = new Map(data.endpoints.map((endpoint) => [endpoint.id, endpoint]))
  const counts = data.deliveries.reduce((result, delivery) => {
    result[delivery.status] = (result[delivery.status] || 0) + 1
    return result
  }, {})
  const term = query.trim().toLowerCase()
  const records = (view === 'deliveries' ? data.deliveries : data.endpoints)
    .filter((item) => {
      if (view === 'deliveries' && status !== 'all' && item.status !== status) return false
      if (view === 'endpoints' && status !== 'all' && item.enabled !== (status === 'enabled')) return false
      return [item.id, item.eventId, item.endpointId, item.name, item.url, endpointsById.get(item.endpointId)?.name, item.lastError]
        .some((value) => value?.toLowerCase().includes(term))
    })
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  const pages = Math.max(1, Math.ceil(records.length / PAGE_SIZE))
  const currentPage = Math.min(page, pages)
  const visible = records.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)
  const selected = data.deliveries.find((delivery) => delivery.id === selectedId)
  const error = view === 'deliveries' ? data.deliveryError : data.endpointError
  const stats = [
    { label: 'Total deliveries', value: data.deliveries.length, icon: Send, tone: 'neutral', filter: 'all' },
    { label: 'Succeeded', value: counts.SUCCEEDED || 0, icon: CheckCircle2, tone: 'green', filter: 'SUCCEEDED' },
    { label: 'Retry scheduled', value: counts.RETRY_SCHEDULED || 0, icon: RefreshCw, tone: 'amber', filter: 'RETRY_SCHEDULED' },
    { label: 'Failed', value: counts.FAILED || 0, icon: XCircle, tone: 'red', filter: 'FAILED' },
  ]

  function changeView(next) {
    setView(next)
    setQuery('')
    setStatus('all')
    setPage(1)
  }

  function created(result) {
    const eventCreated = modal === 'event'
    setNotice({
      title: eventCreated ? 'Event created' : 'Endpoint added',
      detail: eventCreated ? `${result.deliveryCount} deliveries created` : result.name,
      id: result.id,
    })
    setModal(null)
    changeView(eventCreated ? 'deliveries' : 'endpoints')
    setRefreshKey((n) => n + 1)
  }

  return <div className="workspace">
    <aside className="sidebar">
      <a className="brand" href="#" onClick={(event) => { event.preventDefault(); changeView('deliveries') }}>
        <span className="brand-mark"><Webhook size={25} /></span>
        <span>Webhook<span className="brand-sub">DELIVERY PLATFORM</span></span>
      </a>
      <div className="workspace-label">
        <span className="workspace-avatar">W</span>
        <span>Local workspace<small>Development</small></span>
      </div>
      <span className="nav-label">WORKSPACE</span>
      <nav aria-label="Main navigation">
        <button className={view === 'deliveries' ? 'nav-item active' : 'nav-item'}
          aria-current={view === 'deliveries' ? 'page' : undefined}
          onClick={() => changeView('deliveries')}>
          <Activity size={18} />Deliveries<span className="nav-count">{data.loaded ? data.deliveries.length : '\u2014'}</span>
        </button>
        <button className={view === 'endpoints' ? 'nav-item active' : 'nav-item'}
          aria-current={view === 'endpoints' ? 'page' : undefined}
          onClick={() => changeView('endpoints')}>
          <Webhook size={18} />Endpoints<span className="nav-count">{data.endpointsLoaded ? data.endpoints.length : '\u2014'}</span>
        </button>
      </nav>
      <div className="sidebar-bottom"><span className="environment-dot" />Local environment<code>v0.1</code></div>
    </aside>

    <div className="main-shell">
      <header className="topbar">
        <div className="breadcrumb">Workspace<ChevronRight size={13} /><strong>{view === 'deliveries' ? 'Deliveries' : 'Endpoints'}</strong></div>
        <span className="environment-label"><Server size={13} />Development</span>
      </header>

      <main>
        <div className="page-heading">
          <div><span className="eyebrow">WEBHOOK OPERATIONS</span><h1>{view === 'deliveries' ? 'Deliveries' : 'Endpoints'}</h1></div>
          <div className="heading-actions">
            <IconButton label="Refresh data" disabled={refreshing} onClick={() => setRefreshKey((n) => n + 1)}>
              <RefreshCw size={17} className={refreshing ? 'spin' : ''} />
            </IconButton>
            <button className="button primary" onClick={() => setModal(view === 'deliveries' ? 'event' : 'endpoint')}>
              <Plus size={16} />{view === 'deliveries' ? 'Create event' : 'Add endpoint'}
            </button>
          </div>
        </div>

        {notice && <div className="notice green success-notice" role="status">
          <CheckCircle2 size={17} />
          <div><strong>{notice.title}</strong><span>{notice.detail} <code>{shortId(notice.id)}</code></span></div>
          <IconButton label="Dismiss notification" onClick={() => setNotice(null)}><X size={16} /></IconButton>
        </div>}

        {view === 'deliveries' ? <section className="stats" aria-label="Delivery totals">
          {stats.map(({ label, value, icon: Icon, tone, filter }) =>
            <button key={label} aria-pressed={status === filter}
              className={`stat ${status === filter ? 'stat-selected' : ''}`}
              onClick={() => { setStatus(filter); setPage(1) }}>
              <span className="stat-label"><Icon size={15} className={tone} />{label}</span>
              <strong>{!data.loaded || (data.deliveryError && !data.updatedAt) ? '\u2014' : value.toLocaleString()}</strong>
              <span className={`stat-line ${tone}`} />
            </button>)}
        </section> : <div className="endpoint-summary">
          <Webhook size={18} /><strong>{data.endpointsLoaded ? data.endpoints.length : '\u2014'}</strong> registered endpoints
        </div>}

        <section className="records" aria-label={view === 'deliveries' ? 'Delivery records' : 'Endpoint records'}>
          <div className="records-heading">
            <h2>{view === 'deliveries' ? 'Delivery log' : 'Registered endpoints'}</h2>
            <label className="live-control"><input type="checkbox" checked={autoRefresh}
              onChange={(event) => setAutoRefresh(event.target.checked)} />
              <span className="live-dot" />Auto-refresh <span>10s</span>
            </label>
          </div>
          <div className="toolbar">
            <div className="search-field">
              <Search size={16} />
              <input aria-label="Search records" placeholder={view === 'deliveries' ? 'Search ID, endpoint or error...' : 'Search name, URL or ID...'}
                value={query} onChange={(event) => { setQuery(event.target.value); setPage(1) }} />
              {query && <IconButton label="Clear search" onClick={() => { setQuery(''); setPage(1) }}><X size={14} /></IconButton>}
            </div>
            {view === 'deliveries' && <select aria-label="Filter by status" value={status}
              onChange={(event) => { setStatus(event.target.value); setPage(1) }}>
              <option value="all">All statuses</option>
              {Object.entries(STATUS).filter(([key]) => key !== 'STARTED').map(([key, item]) =>
                <option key={key} value={key}>{item.label}</option>)}
            </select>}
            {view === 'endpoints' && <select aria-label="Filter endpoints by status" value={status}
              onChange={(event) => { setStatus(event.target.value); setPage(1) }}>
              <option value="all">All statuses</option>
              <option value="enabled">Enabled</option>
              <option value="disabled">Disabled</option>
            </select>}
            <span className="record-count">{records.length} results</span>
          </div>

          {error && <div className="notice red" role="alert">
            <CircleAlert size={17} />
            <div><strong>Could not refresh {view}</strong>
              <span>{error}{records.length > 0 ? ' Showing previously loaded records.' : ''}</span></div>
            <button className="text-button" onClick={() => setRefreshKey((n) => n + 1)}>Retry</button>
          </div>}

          {!data.loaded ? <div className="empty"><LoaderCircle className="spin" size={26} /><strong>Loading {view}...</strong></div>
            : records.length === 0 ? <div className="empty">
              <span className="empty-icon">{error ? <CircleAlert size={30} /> : <Inbox size={30} />}</span>
              <h3>{error ? 'Data unavailable' : term || status !== 'all' ? 'No matching records' : `No ${view} yet`}</h3>
              {!error && <button className="button"
                onClick={() => {
                  if (term || status !== 'all') { setQuery(''); setStatus('all'); setPage(1) }
                  else setModal(view === 'deliveries' ? 'event' : 'endpoint')
                }}>
                {term || status !== 'all' ? 'Clear filters' : view === 'deliveries' ? 'Create event' : 'Add endpoint'}
              </button>}
            </div> : <div className="table-scroll"><table className={view === 'deliveries' ? 'delivery-table' : 'endpoint-table'}>
              <thead>{view === 'deliveries' ?
                <tr><th>Delivery</th><th>Endpoint</th><th>Status</th><th className="numeric">Attempts</th><th>Created <ArrowDown size={12} /></th><th><span className="sr-only">Details</span></th></tr>
                : <tr><th>Endpoint</th><th>URL</th><th>Status</th><th>Created <ArrowDown size={12} /></th><th>ID</th></tr>}</thead>
              <tbody>{visible.map((row) => view === 'deliveries' ?
                <tr key={row.id}>
                  <td><button className="record-link mono" onClick={() => setSelectedId(row.id)}>{shortId(row.id)}</button>
                    <small className="cell-secondary mono">evt / {shortId(row.eventId)}</small></td>
                  <td><span className="endpoint-cell">{endpointsById.get(row.endpointId)?.name || shortId(row.endpointId)}</span>
                    {row.lastError && <small className="cell-secondary error-preview" title={row.lastError}>{row.lastError}</small>}</td>
                  <td><Status value={row.status} /></td>
                  <td className="numeric mono">{row.attemptCount}</td>
                  <td className="date-cell">{dateTime(row.createdAt)}</td>
                  <td><IconButton label={`View delivery ${shortId(row.id)}`} onClick={() => setSelectedId(row.id)}><ChevronRight size={16} /></IconButton></td>
                </tr>
                : <tr key={row.id}>
                  <td><strong className="endpoint-cell">{row.name}</strong></td>
                  <td><span className="url-cell mono" title={row.url}>{row.url}</span></td>
                  <td><span className={`status ${row.enabled ? 'green' : 'neutral'}`}>
                    {row.enabled ? <CheckCircle2 size={13} /> : <Clock3 size={13} />}
                    {row.enabled ? 'Enabled' : 'Disabled'}</span></td>
                  <td className="date-cell">{dateTime(row.createdAt)}</td>
                  <td><span className="inline-id"><code>{shortId(row.id)}</code><CopyButton value={row.id} /></span></td>
                </tr>)}</tbody>
            </table></div>}

          <footer className="table-footer">
            <span>{records.length ? `${(currentPage - 1) * PAGE_SIZE + 1}\u2013${Math.min(currentPage * PAGE_SIZE, records.length)} of ${records.length}` : '0 records'}
              <span className="footer-scope">{view === 'deliveries' ? 'Loaded deliveries' : 'Loaded endpoints'}</span>
            </span>
            <div className="pagination">
              <IconButton label="Previous page" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}><ChevronLeft size={16} /></IconButton>
              <span>{currentPage} / {pages}</span>
              <IconButton label="Next page" disabled={currentPage === pages} onClick={() => setPage(currentPage + 1)}><ChevronRight size={16} /></IconButton>
            </div>
          </footer>
        </section>
        <footer className="workspace-footer">
          <span className={data.deliveryError || data.endpointError ? 'red' : 'muted'}>
            <span className={`connection-dot ${data.deliveryError || data.endpointError ? 'red' : 'green'}`} />
            {data.deliveryError || data.endpointError ? 'Connection needs attention' : data.updatedAt ? 'API connected' : 'Connecting...'}
          </span>
          <span>{data.updatedAt ? `Last synced ${data.updatedAt.toLocaleTimeString()}` : 'Not synced'}</span>
        </footer>
      </main>
    </div>
    {modal && <CreateDialog key={modal} kind={modal}
      endpointCount={data.endpointsLoaded && !data.endpointError ? data.endpoints.length : null}
      onClose={() => setModal(null)} onCreated={created} />}
    {selected && <DeliveryDetail key={selected.id} delivery={selected}
      endpoint={endpointsById.get(selected.endpointId)} revision={refreshKey}
      onClose={() => setSelectedId(null)} />}
  </div>
}

export default App
