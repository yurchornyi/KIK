/** Count bytes while reading, including chunked bodies without Content-Length. */
export async function readJson(request, max = 1024 * 1024) {
  const fail = (message, status = 400) => { throw Object.assign(Error(message), {status}); };
  if (Number(request.headers.get('Content-Length')) > max) fail('Завеликий запит', 413);
  const reader = request.body?.getReader(); if (!reader) fail('Порожній запит');
  const chunks = []; let size = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read(); if (done) break;
      size += value.byteLength; if (size > max) fail('Завеликий запит', 413);
      chunks.push(value);
    }
  } finally { await reader.cancel(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  let value;
  try { value = JSON.parse(new TextDecoder().decode(bytes)); } catch { fail('Некоректний JSON'); }
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail('Некоректний JSON');
  return value;
}
