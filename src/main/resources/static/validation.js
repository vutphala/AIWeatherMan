export const MAX_QUERY_LENGTH = 500;
// The forecast length is not hardcoded here: it comes from the server's
// weatherman.forecast.days setting via GET /api/config.

export function validateQuery(text) {
  if (typeof text !== 'string' || text.trim().length === 0) {
    return { ok: false, message: 'A query is required.' };
  }
  if (text.length > MAX_QUERY_LENGTH) {
    return { ok: false, message: `The query must be at most ${MAX_QUERY_LENGTH} characters.` };
  }
  return { ok: true };
}

const isNum = (v) => typeof v === 'number' && Number.isFinite(v);
const isStr = (v) => typeof v === 'string' && v.trim().length > 0;

export function validateForecast(f, dayCount) {
  if (!f || typeof f !== 'object' || !f.location || !isStr(f.location.name)) return false;
  if (!Array.isArray(f.days) || f.days.length !== dayCount) return false;
  return f.days.every(
    (d) =>
      d &&
      isStr(d.date) &&
      isNum(d.highC) &&
      isNum(d.lowC) &&
      isStr(d.conditions) &&
      isStr(d.conditionCode) &&
      isNum(d.precipitationProbabilityPct) &&
      d.precipitationProbabilityPct >= 0 &&
      d.precipitationProbabilityPct <= 100 &&
      isNum(d.windSpeedKmh)
  );
}
