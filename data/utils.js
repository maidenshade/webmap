// utils.js
export function cleanName(name) {
  return name
    .toLowerCase()
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9 ]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function normalizeOwnerName(name, aliasMap) {
  const cleanedInput = cleanName(name);
  const inputTokens = new Set(cleanedInput.split(' '));

  for (const [canonical, variants] of Object.entries(aliasMap)) {
    const canonicalTokens = new Set(cleanName(canonical).split(' '));

    const canonicalMatch =
      [...canonicalTokens].every(t => inputTokens.has(t)) ||
      [...inputTokens].every(t => canonicalTokens.has(t));

    if (canonicalMatch) return canonical;

    for (const variant of variants) {
      const variantTokens = new Set(cleanName(variant).split(' '));

      const match =
        [...variantTokens].every(t => inputTokens.has(t)) ||
        [...inputTokens].every(t => variantTokens.has(t));

      if (match) return canonical;
    }
  }
  return name;
}

export function toTitleCase(str) {
  return str
    .toLowerCase()
    .split(' ')
    .map(word =>
      word.length ? word[0].toUpperCase() + word.slice(1) : ''
    )
    .join(' ');
}


export function generateColorRamp(metricId, metrics) {
  const metric = metrics.find(m => m.id === metricId);

  if (!metric || !metric.stops) {
    console.warn(`⚠️ Metric "${metricId}" not found or missing stops`, metric);
    return ['case', ['==', ['get', metricId], null], '#ccc', '#ccc'];
  }

  return [
    'interpolate',
    ['linear'],
    ['get', metricId],
    ...metric.stops.flat()
  ];
}

// export function groupOwners(owners, aliasMap) {
//   const grouped = {};
//
//   owners.forEach(owner => {
//     const original = owner.owner;
//     const normalized = normalizeOwnerName(original);
//     const alias = aliasMap[normalized] || original;
//
//     if (!grouped[alias]) {
//       grouped[alias] = {
//         name: alias,
//         ll_gisacre: 0,
//         percent_total: 0,
//         originalNames: new Set()
//       };
//     }
//
//     grouped[alias].ll_gisacre += parseFloat(owner.ll_gisacre || 0);
//     grouped[alias].percent_total += parseFloat(owner.percent_total || 0);
//     grouped[alias].originalNames.add(original);
//   });
//
//   // Convert Set to Array and sort by acres descending
//   return Object.values(grouped).map(g => ({
//     ...g,
//     originalNames: Array.from(g.originalNames)
//   })).sort((a, b) => b.ll_gisacre - a.ll_gisacre);
// }


export function groupOwners(owners, aliasMap) {
  const grouped = {};

  owners.forEach(owner => {
    const originalName = owner.standardiz || '';
    const normName = normalizeOwnerName(originalName, aliasMap);

    if (!grouped[normName]) {
      grouped[normName] = {
        ll_gisacre: 0,
        percent_total: 0,
        originalNames: new Set()
      };
    }

    grouped[normName].ll_gisacre += parseFloat(owner.ll_gisacre || 0);
    grouped[normName].percent_total += parseFloat(owner.percent_total || 0);
    grouped[normName].originalNames.add(originalName);
  });

  return Object.entries(grouped).map(([name, stats]) => ({
    name,
    ll_gisacre: stats.ll_gisacre,
    percent_total: stats.percent_total,
    originalNames: Array.from(stats.originalNames).sort()
  })).sort((a, b) => b.percent_total - a.percent_total);
}
