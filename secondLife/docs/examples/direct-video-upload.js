/** Browser example for React/Vue. API secret never enters the browser. */
export async function uploadEvidenceVideo({ file, token, apiBase = "", requestId = crypto.randomUUID(),
    onProgress = () => {}, signal }) {
  if (!file || file.size <= 0 || !/\.(mp4|mov|webm)$/i.test(file.name)) {
    throw new Error("Chọn video MP4, MOV hoặc WEBM hợp lệ.");
  }
  const session = await backend("/api/video/upload-signature", { requestId });
  if (file.size > session.maxBytes) throw new Error("Video vượt giới hạn dung lượng.");
  const uploadTag = session.uploadId;
  let last;
  for (let start = 0; start < file.size; start += session.chunkSizeBytes) {
    signal?.throwIfAborted();
    if (Date.now() >= Date.parse(session.expiresAt)) throw new Error("Phiên upload đã hết hạn.");
    const end = Math.min(start + session.chunkSizeBytes, file.size);
    for (let attempt = 0; ; attempt++) {
      signal?.throwIfAborted();
      const form = new FormData();
      for (const [key, value] of Object.entries(session.parameters)) form.append(key, String(value));
      form.append("api_key", session.apiKey);
      form.append("signature", session.signature);
      form.append("file", file.slice(start, end, file.type), file.name);
      let response;
      try {
        response = await fetch(session.uploadUrl, {
          method: "POST", body: form, signal,
          headers: { "X-Unique-Upload-Id": uploadTag, "Content-Range": `bytes ${start}-${end - 1}/${file.size}` }
        });
      } catch (error) {
        if (signal?.aborted || attempt >= 2) throw error;
        await delay(500 * 2 ** attempt);
        continue;
      }
      if ((response.status === 429 || response.status >= 500) && attempt < 2) {
        await delay(500 * 2 ** attempt);
        continue;
      }
      const body = await response.json();
      if (!response.ok) throw new Error(body.error?.message || "Cloudinary upload thất bại.");
      last = body;
      break;
    }
    // This is acknowledged progress per chunk, not byte-level network progress.
    onProgress({ uploadedBytes: end, totalBytes: file.size, percent: Math.round(end * 100 / file.size), uploadId: session.uploadId });
  }
  // Full asset responses can omit done (for example an existing immutable asset).
  // Never accept an explicit partial response, and keep BE provider verification authoritative.
  if (last?.done === false || last?.public_id !== session.publicId ||
      !last?.secure_url || !Number.isSafeInteger(last?.bytes) || last.bytes <= 0 || !last?.format) {
    throw new Error("Cloudinary chưa trả metadata của video đã upload hoàn tất.");
  }
  if (last.bytes !== file.size) {
    throw new Error("Session đang trỏ tới video có dung lượng khác. Xin session mới cho file mới.");
  }
  return backend("/api/video/complete", {
    uploadId: session.uploadId, publicId: last.public_id, videoUrl: last.secure_url,
    bytes: last.bytes, format: last.format, duration: last.duration
  });

  async function backend(path, body) {
    const response = await fetch(`${apiBase}${path}`, {
      method: "POST", signal,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || `HTTP ${response.status}`);
    }
    return payload.data;
  }
}

function delay(milliseconds) { return new Promise(resolve => setTimeout(resolve, milliseconds)); }

/** Use for VIDEO metadata.contentUrl, with the viewer's JWT. URL goes to Cloudinary, not BE. */
export async function getEvidencePlayback({ contentUrl, token, apiBase = "", signal }) {
  const response = await fetch(`${apiBase}${contentUrl}`, { signal, headers: { Authorization: `Bearer ${token}` } });
  const payload = await response.json();
  if (!response.ok || !payload.success) throw new Error(payload.message || "Không thể xem bằng chứng.");
  return payload.data; // {url, expiresAt}; player.src=url; request a new URL after expiry.
}
