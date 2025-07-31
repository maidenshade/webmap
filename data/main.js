import { cleanName, normalizeOwnerName, groupOwners, generateColorRamp } from './utils.js';
import { initMap } from './map.js';
import { setupSidebar, updateSidebar, showTooltip, hideTooltip } from './ui.js';


console.log("✅ main.js is loaded and executing");

let selectedMetricId = null;
let lastSelectedFeature = null;
let selectedViewMode = 'top20';

Promise.all([
  fetch('owner-data-clean.json').then(res => res.json()),
  fetch('alias-map-cleaned.json').then(res => res.json()),
  fetch('metrics.json').then(res => res.json()).then(data => data.metrics),
  fetch('topcorp_all.json').then(res => res.json())
]).then(([ownerData, aliasMap, metrics, corpOwnerData]) => {
  selectedMetricId = metrics[0]?.id || null;


  // Make available globally
  window.corpOwnerData = corpOwnerData;


  setupSidebar(metrics, (newMetricId) => {
    selectedMetricId = newMetricId;
    map.setPaintProperty('summary-fill', 'fill-color', generateColorRamp(newMetricId, metrics));
    if (lastSelectedFeature) {
      updateSidebar(
        lastSelectedFeature.properties,
        ownerData,
        aliasMap,
        metrics,
        selectedMetricId,
        selectedViewMode
      );
    }
  });

  document.getElementById('view-mode').addEventListener('change', (e) => {
  selectedViewMode = e.target.value;

  if (lastSelectedFeature) {
    updateSidebar(
      lastSelectedFeature.properties,
      ownerData,
      aliasMap,
      metrics,
      selectedMetricId,
      selectedViewMode
    );
  }
});


  function onCountyClick(e, map) {
    const clickedFeature = e.features[0];
    lastSelectedFeature = clickedFeature;
    map.getSource('highlighted-county').setData(clickedFeature);
    updateSidebar(clickedFeature.properties, ownerData, aliasMap, metrics, selectedMetricId, selectedViewMode);
  }

  function onCountyHover(e, map) {
  showTooltip(e, map, metrics, selectedMetricId);
  map.getCanvas().style.cursor = 'pointer';
    const geoid = e.features?.[0]?.properties?.geoid;
    map.setFilter(
      'hover-glow',
      geoid ? ['==', 'geoid', geoid] : ['==', 'geoid', '']
  );
}


function onCountyLeave(map) {
  map.getCanvas().style.cursor = '';
  map.setFilter('hover-glow', ['==', 'geoid', '']);
}

  const map = initMap(ownerData, aliasMap, onCountyClick, onCountyHover, onCountyLeave);

  // Initial color ramp
  map.once('load', () => {
    if (selectedMetricId) {
      map.setPaintProperty('summary-fill', 'fill-color', generateColorRamp(selectedMetricId, metrics));
    }
  });
});
