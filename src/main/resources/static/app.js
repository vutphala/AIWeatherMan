import { validateQuery, validateForecast } from './validation.js';
import { buildViewModel } from './viewModel.js';
import { renderChart } from './chart.js';

const REQUEST_TIMEOUT_MS = 30000;
const STILL_WORKING_MS = 10000;
const ICONS = { clear: '☀️', 'partly-cloudy': '⛅', cloudy: '☁️', fog: '🌫️', rain: '🌧️', snow: '❄️', storm: '⛈️', unknown: '🌡️' };

const form = document.getElementById('query-form');
const input = document.getElementById('query');
const button = document.getElementById('submit');
const status = document.getElementById('status');
const loading = document.getElementById('loading');
const graphics = document.getElementById('graphics');

let pending = false;

// Forecast length comes from the server config (weatherman.forecast.days).
const configPromise = fetch('/api/config')
  .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`config ${r.status}`))))
  .catch(() => null);

function setStatus(message, isError = false) {
  status.textContent = message;
  status.classList.toggle('error', isError);
}

function setPending(on) {
  pending = on;
  button.disabled = on;
  loading.hidden = !on;
}

function render(vm) {
  const frag = document.createDocumentFragment();

  const h = document.createElement('h2');
  h.textContent = vm.locationLabel;
  frag.append(h);

  const cards = document.createElement('ol');
  cards.className = 'days';
  for (const d of vm.days) {
    const li = document.createElement('li');
    li.className = 'day';
    const parts = [
      ['date', d.dateLabel],
      ['icon', ICONS[d.iconKey] ?? ICONS.unknown],
      ['temps', `${d.highLabel} / ${d.lowLabel}`],
      ['cond', d.conditions],
      ['precip', `Rain ${d.precipLabel}`],
      ['wind', `Wind ${d.windLabel}`],
    ];
    for (const [cls, text] of parts) {
      const s = document.createElement('span');
      s.className = cls;
      s.textContent = text;
      if (cls === 'icon') s.setAttribute('aria-hidden', 'true');
      li.append(s);
    }
    cards.append(li);
  }
  frag.append(cards);
  frag.append(renderChart(vm.chart));

  // Visually hidden data table as a text alternative for the chart.
  const table = document.createElement('table');
  table.className = 'sr-only';
  table.innerHTML = '<caption>Temperature data</caption><thead><tr><th>Date</th><th>High</th><th>Low</th></tr></thead>';
  const tbody = document.createElement('tbody');
  vm.chart.dates.forEach((date, i) => {
    const tr = document.createElement('tr');
    for (const v of [date, `${vm.chart.highs[i]}°C`, `${vm.chart.lows[i]}°C`]) {
      const td = document.createElement('td');
      td.textContent = v;
      tr.append(td);
    }
    tbody.append(tr);
  });
  table.append(tbody);
  frag.append(table);

  graphics.replaceChildren(frag); // replaces all previous content
}

async function submit() {
  if (pending) return;
  const text = input.value;
  const check = validateQuery(text);
  if (!check.ok) {
    setStatus(check.message, true);
    return;
  }

  setPending(true);
  setStatus('');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const notice = setTimeout(() => setStatus('Still working, this can take a while…'), STILL_WORKING_MS);
  try {
    const res = await fetch('/api/GetWeatherForecast', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: text }),
      signal: controller.signal,
    });
    let body = null;
    try {
      body = await res.json();
    } catch {
      /* non-JSON response */
    }
    if (!res.ok) {
      setStatus(body?.message ?? `The service returned an error (${res.status}).`, true);
      return;
    }
    const config = await configPromise;
    if (!config) {
      setStatus('Could not load the app configuration. Reload the page and try again.', true);
      return;
    }
    // if (!validateForecast(body, config.forecastDays)) {
    //   setStatus('The forecast was incomplete, so it was not shown.', true);
    //   return;
    // }
    render(buildViewModel(body));
    setStatus('');
  } catch (e) {
    setStatus(e.name === 'AbortError' ? 'The request timed out.' : 'Could not reach the service.', true);
  } finally {
    clearTimeout(timeout);
    clearTimeout(notice);
    setPending(false);
  }
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  submit();
});
