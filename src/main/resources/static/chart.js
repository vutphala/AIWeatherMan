const NS = 'http://www.w3.org/2000/svg';

function el(name, attrs = {}, text) {
  const e = document.createElementNS(NS, name);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  if (text !== undefined) e.textContent = text;
  return e;
}

/** Renders a high/low temperature trend into a new SVG element. */
export function renderChart(chart) {
  const W = 720, H = 280, L = 48, R = 16, T = 16, B = 44;
  const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': 'Temperature trend, high and low in degrees Celsius' });
  const all = [...chart.highs, ...chart.lows];
  let min = Math.floor(Math.min(...all)), max = Math.ceil(Math.max(...all));
  if (min === max) max = min + 1;
  const n = chart.dates.length;
  const x = (i) => L + (i * (W - L - R)) / Math.max(1, n - 1);
  const y = (v) => T + ((max - v) * (H - T - B)) / (max - min);

  for (let t = 0; t <= 4; t++) {
    const v = min + ((max - min) * t) / 4;
    svg.append(el('line', { x1: L, x2: W - R, y1: y(v), y2: y(v), class: 'grid' }));
    svg.append(el('text', { x: L - 6, y: y(v) + 4, class: 'axis', 'text-anchor': 'end' }, `${Math.round(v)}°`));
  }
  chart.dates.forEach((d, i) => {
    svg.append(el('text', { x: x(i), y: H - 22, class: 'axis', 'text-anchor': 'middle' }, d.slice(5)));
  });
  const line = (values, cls) => {
    svg.append(el('polyline', { points: values.map((v, i) => `${x(i)},${y(v)}`).join(' '), class: cls, fill: 'none' }));
    values.forEach((v, i) => svg.append(el('circle', { cx: x(i), cy: y(v), r: 3, class: cls + '-dot' })));
  };
  line(chart.highs, 'high');
  line(chart.lows, 'low');
  svg.append(el('text', { x: L, y: H - 4, class: 'legend high-label' }, '— High'));
  svg.append(el('text', { x: L + 70, y: H - 4, class: 'legend low-label' }, '— Low'));
  return svg;
}
