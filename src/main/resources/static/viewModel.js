export const MAX_LOCATION_LABEL = 100;

export function buildViewModel(forecast) {
  const days = [...forecast.days].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  const name = forecast.location.name;
  const locationLabel = [...name].slice(0, MAX_LOCATION_LABEL).join('');
  return {
    locationLabel,
    days: days.map((d) => ({
      dateLabel: d.date,
      iconKey: d.conditionCode,
      conditions: d.conditions,
      highLabel: `${d.highC}°C`,
      lowLabel: `${d.lowC}°C`,
      precipLabel: `${d.precipitationProbabilityPct}%`,
      windLabel: `${d.windSpeedKmh} km/h`,
    })),
    chart: {
      dates: days.map((d) => d.date),
      highs: days.map((d) => d.highC),
      lows: days.map((d) => d.lowC),
    },
  };
}
