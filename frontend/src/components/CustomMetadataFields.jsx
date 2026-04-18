/**
 * Champs métier dynamiques selon document_types.custom_fields_schema (API).
 */
export function getSchemaFields(schema) {
  if (!schema || typeof schema !== 'object') return [];
  const fields = schema.fields;
  return Array.isArray(fields) ? fields : [];
}

/** @param {Array} fields from getSchemaFields */
export function buildCustomPayload(fields, state) {
  if (!fields?.length) return null;
  const out = {};
  for (const f of fields) {
    const raw = state[f.key];
    if (raw === undefined || raw === null || raw === '') continue;
    const t = (f.type || 'STRING').toUpperCase();
    if (t === 'NUMBER') {
      const n = Number(String(raw).replace(',', '.'));
      if (!Number.isFinite(n)) continue;
      out[f.key] = n;
    } else if (t === 'BOOLEAN') {
      out[f.key] = Boolean(raw);
    } else if (t === 'DATE') {
      out[f.key] = String(raw).trim();
    } else {
      out[f.key] = String(raw).trim();
    }
  }
  return Object.keys(out).length ? out : null;
}

export default function CustomMetadataFields({ schema, value, onChange, lang }) {
  const fields = getSchemaFields(schema);
  if (!fields.length) return null;

  function setKey(k, v) {
    onChange({ ...value, [k]: v });
  }

  return (
    <div className="space-y-3 border-t border-slate-200 pt-3 mt-1">
      <p className="text-xs font-medium text-slate-600">
        {lang === 'pt' ? 'Campos específicos do tipo' : 'Champs spécifiques au type'}
      </p>
      {fields.map((f) => {
        const label = lang === 'pt' ? f.labelPt || f.labelFr : f.labelFr || f.labelPt;
        const t = (f.type || 'STRING').toUpperCase();
        const req = !!f.required;
        const v = value[f.key] ?? '';
        if (t === 'BOOLEAN') {
          return (
            <label key={f.key} className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={!!v}
                onChange={(e) => setKey(f.key, e.target.checked)}
              />
              <span className="text-slate-700">
                {label}
                {req ? ' *' : ''}
              </span>
            </label>
          );
        }
        if (t === 'DATE') {
          return (
            <label key={f.key} className="block">
              <span className="text-slate-600">
                {label}
                {req ? ' *' : ''}
              </span>
              <input
                type="date"
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
                value={v || ''}
                onChange={(e) => setKey(f.key, e.target.value)}
                required={req}
              />
            </label>
          );
        }
        if (t === 'NUMBER') {
          return (
            <label key={f.key} className="block">
              <span className="text-slate-600">
                {label}
                {req ? ' *' : ''}
              </span>
              <input
                type="number"
                step="any"
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
                value={v}
                onChange={(e) => setKey(f.key, e.target.value)}
                required={req}
              />
            </label>
          );
        }
        return (
          <label key={f.key} className="block">
            <span className="text-slate-600">
              {label}
              {req ? ' *' : ''}
            </span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={v}
              onChange={(e) => setKey(f.key, e.target.value)}
              required={req}
              maxLength={f.maxLength || undefined}
            />
          </label>
        );
      })}
    </div>
  );
}
