const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

export async function createDocument(name, html, userId = 'demo-user') {
  const res = await fetch(`${API_BASE}/api/documents`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId
    },
    body: JSON.stringify({ name, html })
  });
  if (!res.ok) throw new Error(`Create failed: ${res.status}`);
  return res.json();
}

export async function getDocument(id) {
  const res = await fetch(`${API_BASE}/api/documents/${id}`);
  if (!res.ok) throw new Error(`Load failed: ${res.status}`);
  return res.json();
}

export async function saveDocument(id, payload, userId = 'demo-user') {
  const res = await fetch(`${API_BASE}/api/documents/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId
    },
    body: JSON.stringify(payload)
  });
  if (res.status === 409) {
    const conflict = await res.json();
    const err = new Error('Version conflict');
    err.conflict = conflict;
    throw err;
  }
  if (!res.ok) throw new Error(`Save failed: ${res.status}`);
  return res.json();
}

export { API_BASE };
