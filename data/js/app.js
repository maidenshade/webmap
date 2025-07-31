const map = new maplibregl.Map({
  container: 'map',
  style: 'http://localhost:8080/styles/summary-style/style.json',
  center: [-98.5795, 39.8283], // USA center
  zoom: 4
});

// ✅ Helper: Convert string to Title Case
function toTitleCase(str) {
  return str
    .toLowerCase()
    .replace(/\b\w/g, char => char.toUpperCase());
}

map.on('load', () => {
  fetch('owner-data-clean.json')
    .then(res => res.json())
    .then(ownerData => {
      map.on('click', 'summary-fill', (e) => {
        const props = e.features[0].properties;
        const geoid = props.geoid || props.GEOID || props.GEOID10;
        const gini = props.gini_private;
        const owners = ownerData[geoid];
        const name = props.NAME

        let html = `<h3>County ${NAME}</h3>`;
        html += `<p><strong>Land Ownership Gini Index (excludes public land):</strong> ${gini}</p>`;
        html += `<p><strong>Land Ownership Gini Index</strong> (excludes public land): ${parseFloat(gini).toFixed(3)}</p>`;


        if (owners && owners.length > 0) {
          // ✅ Sort owners descending by percent_total
          const sortedOwners = owners
            .filter(owner => owner.percent_total !== null && owner.percent_total !== undefined)
            .sort((a, b) => parseFloat(b.percent_total) - parseFloat(a.percent_total));

          html += `<h4>Top owners, total acres, and percent of county owned:</h4><ul>`;
          sortedOwners.forEach(owner => {
            const ownerName = toTitleCase(owner.standardiz);
            const acres = parseFloat(owner.ll_gisacre).toLocaleString();
            const percent = parseFloat(owner.percent_total).toFixed(2);
            html += `<li>${owner.standardiz} — ${Math.round(owner.ll_gisacre)} acres (${(owner.percent_total * 100).toFixed(0)}%)</li>`;
          });
          html += `</ul>`;
        } else {
          html += `<p>No owner data available.</p>`;
        }

        document.getElementById('sidebar').innerHTML = html;
      });
    });
});
