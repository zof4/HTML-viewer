import { useEffect, useMemo, useRef, useState } from 'react';
import { connectDocumentTopic } from './collab';
import { createDocument, getDocument, saveDocument } from './api';

const DEFAULT_HTML = `<main><h1>Welcome</h1><p>Paste HTML here and it syncs globally.</p></main>`;

function useDebouncedCallback(callback, delay) {
  const timerRef = useRef();
  return (value) => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => callback(value), delay);
  };
}

export default function App() {
  const [documentId, setDocumentId] = useState('');
  const [name, setName] = useState('Global Page');
  const [html, setHtml] = useState(DEFAULT_HTML);
  const [version, setVersion] = useState(0);
  const [status, setStatus] = useState('Not connected');
  const [updatedBy, setUpdatedBy] = useState('');
  const [mobileTab, setMobileTab] = useState('edit');

  const userId = useMemo(() => {
    const existing = localStorage.getItem('demo-user-id');
    if (existing) return existing;
    const generated = `user-${Math.random().toString(36).slice(2, 8)}`;
    localStorage.setItem('demo-user-id', generated);
    return generated;
  }, []);

  useEffect(() => {
    let unsubscribe = () => {};
    if (documentId) {
      unsubscribe = connectDocumentTopic(documentId, async () => {
        const latest = await getDocument(documentId);
        setHtml(latest.currentHtml);
        setVersion(latest.currentVersion);
        setUpdatedBy(latest.updatedBy);
        setStatus(`Synced v${latest.currentVersion}`);
      });
    }
    return () => unsubscribe();
  }, [documentId]);

  const debouncedSave = useDebouncedCallback(async (nextHtml) => {
    if (!documentId || version === 0) return;
    try {
      setStatus('Saving...');
      const result = await saveDocument(documentId, {
        baseVersion: version,
        html: nextHtml,
        clientId: userId
      }, userId);
      setVersion(result.newVersion);
      setStatus(`Saved v${result.newVersion}`);
    } catch (error) {
      if (error.conflict) {
        setHtml(error.conflict.currentHtml);
        setVersion(error.conflict.currentVersion);
        setStatus(`Conflict. Reloaded v${error.conflict.currentVersion}`);
      } else {
        setStatus(`Save failed: ${error.message}`);
      }
    }
  }, 600);

  const onHtmlChange = (value) => {
    setHtml(value);
    debouncedSave(value);
  };

  const onCreate = async () => {
    const doc = await createDocument(name, html, userId);
    setDocumentId(doc.id);
    setVersion(doc.currentVersion);
    setStatus(`Created v${doc.currentVersion}`);
    setUpdatedBy(doc.updatedBy);
  };

  const onLoad = async () => {
    const doc = await getDocument(documentId);
    setName(doc.name);
    setHtml(doc.currentHtml);
    setVersion(doc.currentVersion);
    setUpdatedBy(doc.updatedBy);
    setStatus(`Loaded v${doc.currentVersion}`);
  };

  return (
    <div className="container">
      <header>
        <h1>Collaborative HTML Viewer</h1>
        <p>User: <strong>{userId}</strong> | {status} {updatedBy ? `| last by ${updatedBy}` : ''}</p>
      </header>

      <section className="controls">
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Document name" />
        <input value={documentId} onChange={(e) => setDocumentId(e.target.value)} placeholder="Document ID" />
        <button onClick={onCreate}>Create</button>
        <button onClick={onLoad} disabled={!documentId}>Load</button>
      </section>

      <section className="mobile-tabs">
        <button className={mobileTab === 'edit' ? 'active' : ''} onClick={() => setMobileTab('edit')}>Edit</button>
        <button className={mobileTab === 'preview' ? 'active' : ''} onClick={() => setMobileTab('preview')}>Preview</button>
      </section>

      <main className="editor-grid">
        <div className={`pane ${mobileTab === 'edit' ? 'show' : 'hide-on-mobile'}`}>
          <h2>HTML Editor</h2>
          <textarea
            value={html}
            onChange={(e) => onHtmlChange(e.target.value)}
            spellCheck={false}
          />
        </div>

        <div className={`pane ${mobileTab === 'preview' ? 'show' : 'hide-on-mobile'}`}>
          <h2>Preview (sandbox)</h2>
          <iframe
            title="preview"
            sandbox="allow-forms allow-same-origin"
            srcDoc={html}
          />
        </div>
      </main>
    </div>
  );
}
