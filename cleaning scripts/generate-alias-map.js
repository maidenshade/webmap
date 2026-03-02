const fs = require('fs');

// Load owner data (object: { countyGeoId: [owners] })
const ownerData = JSON.parse(fs.readFileSync('owner-data-clean.json', 'utf8'));

// Utility to clean names for fuzzy comparison
function cleanName(name) {
  return name
    .toLowerCase()
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9 ]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

// Limit to top N owners per county for performance
const TOP_N = 20;

// Score function to pick canonical (high freq and shorter token count)
function scoreCanonical(name, freqMap) {
  const cleaned = cleanName(name);
  const tokenCount = cleaned.split(' ').length;
  const freq = freqMap[name] || 0;
  return freq * 10 - tokenCount;
}

// This will hold final alias groups across all counties
const globalAliasMap = {};

// Process each county individually
for (const [countyId, owners] of Object.entries(ownerData)) {
  // Sort and limit to top N owners by percent_total
  const topOwners = owners
    .slice()
    .sort((a, b) => b.percent_total - a.percent_total)
    .slice(0, TOP_N);

  // Count frequency of each original name in this county
  const freqMap = {};
  topOwners.forEach(({ standardiz }) => {
    const name = standardiz?.trim();
    if (!name) return;
    freqMap[name] = (freqMap[name] || 0) + 1;
  });

  const names = Object.keys(freqMap);

  // Precompute token sets for all names in this county
  const nameTokensMap = {};
  for (const name of names) {
    nameTokensMap[name] = new Set(cleanName(name).split(' '));
  }

  // Group names by fuzzy matching (subset token logic)
  const processed = new Set();

  for (let i = 0; i < names.length; i++) {
    const nameA = names[i];
    if (processed.has(nameA)) continue;

    const tokensA = nameTokensMap[nameA];
    const group = [nameA];

    for (let j = i + 1; j < names.length; j++) {
      const nameB = names[j];
      if (processed.has(nameB)) continue;

      const tokensB = nameTokensMap[nameB];

      // Check if tokensB is subset of tokensA or vice versa (fuzzy match)
      const isSubset =
        [...tokensB].every(t => tokensA.has(t)) ||
        [...tokensA].every(t => tokensB.has(t));

      if (isSubset) {
        group.push(nameB);
        processed.add(nameB);
      }
    }

    processed.add(nameA);

    // Only create alias group if more than one name matched
    if (group.length > 1) {
      group.sort((a, b) => scoreCanonical(b, freqMap) - scoreCanonical(a, freqMap));
      const canonical = group[0].toUpperCase();
      const variants = group.slice(1);

      // Initialize or append to global alias map
      if (!globalAliasMap[canonical]) {
        globalAliasMap[canonical] = new Set();
      }
      variants.forEach(v => globalAliasMap[canonical].add(v));
    }
  }
}

// Convert all Sets to sorted arrays for final output
const finalAliasMap = {};
for (const [canonical, variantsSet] of Object.entries(globalAliasMap)) {
  finalAliasMap[canonical] = Array.from(variantsSet).sort();
}

// Write alias map to file
fs.writeFileSync('alias-map-suggestions.json', JSON.stringify(finalAliasMap, null, 2));
console.log(`✅ alias-map-suggestions.json created with ${Object.keys(finalAliasMap).length} entries.`);
