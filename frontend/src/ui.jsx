import { useEffect, useRef, useState } from 'react'
import { Check, CircleAlert, Copy, X } from 'lucide-react'
import { STATUS } from './data'

export function Status({ value }) {
  const { label, tone, icon: Icon } = STATUS[value] || { label: value || 'Unknown', tone: 'neutral', icon: CircleAlert }
  return <span className={`status ${tone}`}><Icon size={13} />{label}</span>
}

export function IconButton({ label, children, ...props }) {
  return <button type="button" className="icon-button" title={label} aria-label={label} {...props}>{children}</button>
}

export function CopyButton({ value }) {
  const [state, setState] = useState('idle')
  useEffect(() => {
    if (state === 'idle') return
    const timer = setTimeout(() => setState('idle'), 2000)
    return () => clearTimeout(timer)
  }, [state])
  async function copy() {
    try { await navigator.clipboard.writeText(value); setState('copied') } catch { setState('failed') }
  }
  return <span className="copy-control"><IconButton label={state === 'copied' ? 'Copied' : 'Copy ID'} onClick={copy}>{state === 'copied' ? <Check size={14} /> : <Copy size={14} />}</IconButton>{state === 'failed' && <small role="status">Copy unavailable</small>}</span>
}

export function Dialog({ title, drawer = false, onClose, children }) {
  const ref = useRef(null)
  useEffect(() => {
    const dialog = ref.current
    const focused = document.activeElement
    dialog.showModal()
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { dialog.close(); document.body.style.overflow = oldOverflow; focused?.focus() }
  }, [])
  return <dialog ref={ref} className={drawer ? 'dialog drawer' : 'dialog'} aria-labelledby="dialog-title" onCancel={(event) => { event.preventDefault(); onClose() }}>
    <header className="dialog-heading"><h2 id="dialog-title">{title}</h2><IconButton label="Close dialog" onClick={onClose}><X size={18} /></IconButton></header>
    {children}
  </dialog>
}
