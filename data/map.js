// map.js
import { groupOwners } from './utils.js';



export function initMap(ownerData, aliasMap, onCountyClick, onCountyHover, onCountyLeave) {
  const map = new maplibregl.Map({
    container: 'map',
    style: './style.json',
    center: [-98.5795, 39.8283],
    zoom: 3,
    minZoom: 0,
    maxZoom: 14,
    dragRotate: false,
    touchZoomRotate: false,
    pitchWithRotate: false

  });

  map.whenReady(() => {
    // Fit the map to the full extent of the US + Territories
    const bounds = [
      [-179.9, -14.5], // Southwest (American Samoa, Guam, Hawaii)
      [179.9, 72]      // Northeast (Alaska, Northern Mariana Islands)
    ];

    map.fitBounds(bounds, {
      padding: 20,
      maxZoom: 4.5,
      linear: true
    });

    // Optional: force the map to zoom to level 3 if needed
  //  map.setZoom(3); // Only if fitBounds overshoots or loads wrong zoom

    // Ensure the container size is calculated correctly
    setTimeout(() => map.resize(), 100);
  });

  // Resize on window resize (mobile compatibility)
  window.addEventListener('resize', () => {
    map.resize();
  });

  map.on('load', () => {
    map.addSource('highlighted-county', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: [] }
    });

    map.addLayer({
      id: 'highlight-line',
      type: 'line',
      source: 'highlighted-county',
      paint: {
        'line-color': '#ffff00',
        'line-width': 4,
        'line-blur': 2
      }
    });

    map.addLayer({
      id: 'highlight-fill',
      type: 'fill',
      source: 'highlighted-county',
      paint: {
        'fill-color': 'rgba(255, 255, 0, 0.27)',
        'fill-outline-color': '#ffff00'
      }
    });

    map.addLayer({
      id: 'hover-glow',
      type: 'line',
      source: 'combined',
      'source-layer': 'summary',
      paint: {
        'line-color': 'pink',
        'line-width': 6,
        'line-opacity': 0.5,
        'line-blur': 1.5
      },
      filter: ['==', 'geoid', '']
    });

    map.on('click', 'summary-fill', (e) => {
      onCountyClick(e, map, ownerData, aliasMap);
    });

    map.on('mousemove', 'summary-fill', (e) => {
      onCountyHover(e, map);
    });

    map.on('mouseleave', 'summary-fill', () => {
      onCountyLeave(map);
    });
  });

  return map;
}
