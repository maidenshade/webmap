// ui.js
import { groupOwners } from './utils.js';
import { toTitleCase } from './utils.js';


let currentMetricId = 'gini_private'; // Default
let viewMode = 'top20';

export function setupSidebar(metrics, onMetricChange) {
  const sidebar = document.getElementById('sidebar');

  // Build selector and description panel
  sidebar.innerHTML = `
    <label for="metric-select"><strong>Color counties by:</strong></label>
    <select id="metric-select" style="width: 100%; margin-bottom: 1em;">
      ${metrics.map(m => `<option value="${m.id}">${m.name}</option>`).join('')}
    </select>
    <div id="metric-description" style="margin-bottom: 1em; font-size: 0.9em; color: white;"></div>

    <label for="view-mode"><strong>Select county details to view:</strong></label>
    <select id="view-mode" style="width: 100%; margin-bottom: 1em;">
      <option value="top20">Top 20 Owners</option>
      <option value="top5corp">Top 5 Corporate Owners</option>
      <option value="breakdown">Owner Type Breakdown</option>
      <option value="summary">County Data Summary</option>
    </select>

    <div id="sidebar-content"><p>Click on a county to see ownership data.</p></div>

  `;

  const select = document.getElementById('metric-select');
  const description = document.getElementById('metric-description');

  const selectedMetric = metrics.find(m => m.id === currentMetricId);
  if (selectedMetric) description.textContent = selectedMetric.description || '';

  const viewSelect = document.getElementById('view-mode');
  viewSelect.addEventListener('change', () => {
    viewMode = viewSelect.value;
  });

  // Handle selector change
  select.addEventListener('change', () => {
    const metric = metrics.find(m => m.id === select.value);
    if (metric) {
      currentMetricId = metric.id;
      description.textContent = metric.description || '';
      onMetricChange(currentMetricId); // callback to update map paint
    }
  });
}

function generateTopOwnersHTML(topOwners, aliasMap) {
  return `
    <h4>Top Owners</h4>
    <ul>
      ${topOwners.map(o => {
        const rawName = aliasMap[o.standardiz] || o.standardiz;
        const name = toTitleCase(rawName);
        const acres = Math.round(o.ll_gisacre || o.acres || 0).toLocaleString();
        const pct = (o.percent_total || o.pct_total || 0).toFixed(2);
        return `<li><strong>${name}</strong>: ${acres} acres (${pct}%)</li>`;
      }).join('')}
    </ul>
  `;
}

function generateTopCorpOwnersHTML(corpOwners) {
  return `
    <h4>Top 5 Corporate Owners</h4>
    <ul>
      ${corpOwners.map(o => {
        const acres = Math.round(o.acres || 0).toLocaleString();
        const pct = (o.pct_total || 0).toFixed(2);
        return `<li><strong>${o.owner}</strong>: ${acres} acres (${pct}%)</li>`;
      }).join('')}
    </ul>
  `;
}

function generateOwnerTypeBreakdownHTML(props) {
    // Calculate pct_private if it's missing but private_acres and total_acres_y exist
  if (
    props.private_acres != null &&
    props.total_acres_y != null &&
    (props.pct_private == null || isNaN(props.pct_private))
  ) {
    props.pct_private = (props.private_acres / props.total_acres_y) * 100;
  }

  const formatVal = (val, isPercent = false) => {
    if (val === null || val === undefined || isNaN(val)) return null;
    return isPercent ? `${val.toFixed(2)}%` : `${Math.round(val).toLocaleString()} acres`;
  };

  const labelMap = {
    'federal': 'Other federal',
    'state': 'Other state',
    'banking and finance': 'Banking & finance',
    'natural resource corporation': 'Corporate - natural resources & ag',
    'general corporate': 'Corporate - general',
    'individual and unclassified': 'Individual/unclassified',
    'cemetery or funeral home': 'Cemetery/funeral home',
    'colleges and universities': 'Colleges/universities',
    'carceral facilites': 'Carceral facilities'
  };

  const groups = {
    Corporate: [
      'banking and finance',
      'investors',
      'natural resource corporation',
      'general corporate'
    ],
    Private: [
      'individual and unclassified',
      'religious',
      'cemetery or funeral home',
      'colleges and universities',
      'healthcare'
    ],
    Public: [
      'municipal and county',
      'state parks',
      'national parks',
      'tribal',
      'federal',
      'state',
      'carceral facilites'
    ]
  };

  function buildGroupSection(title, keys) {
    let section = `<h4><span style="color: yellow;">${title}</span></h4><ul>`;
    keys.forEach(base => {
      const label = labelMap[base] || base.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
      const acresVal = formatVal(props[`${base}_acres`]);
      const pctVal = formatVal(props[`${base}_pct`] ?? props[`${base}_percent`], true);

      if (acresVal) {
        section += `<li><strong>${label}</strong>: <span style="color: gray"> ${acresVal}</span>, <span style="color: yellow;">${pctVal}</span></li>`;
      } else {
        section += `<li>${label}: N/A</li>`;
      }
    });
    section += '</ul>';
    return section;
  }

  function buildSummarySection() {
    return `
      <h4><span style="color: yellow;">Subtotals:</span></h4>
      <ul>
        <li><strong>Private</strong>: <span style="color: gray;">${formatVal(props.private_acres) || 'N/A'}</span>, <span style="color: yellow;">${formatVal(props.pct_private, true) || ''}</span></li>
        <li><strong>Public</strong>: <span style="color: gray;">${formatVal(props.public_acres) || 'N/A'}</span>, <span style="color: yellow;">${formatVal(props.pct_public, true) || ''}</span></li>
        <li><strong>Unknown</strong>: <span style="color: gray;">${formatVal(props.unknown_acres) || 'N/A'}</span>, <span style="color: yellow;">${formatVal(props.pct_unknown, true) || ''}</span></li>
      </ul>
    `;
  }

  function buildAbsenteeSection() {
    return `
      <h4><span style="color: yellow;">Absentee Ownership</span></h4>
      <ul>
        <li><strong>Out of county</strong>: <span style="color: gray;">${formatVal(props.out_of_county_acres) || 'N/A'}</span>, <span style="color: yellow;">${formatVal(props.out_of_county_percent, true) || ''}</span></li>
        <li><strong>Out of state</strong>: <span style="color: gray;">${formatVal(props.out_of_state_acres) || 'N/A'}</span>, <span style="color: yellow;">${formatVal(props.out_of_state_percent, true) || ''}</span></li>
      </ul>
    `;
  }

  return `
    ${buildGroupSection('Corporate Owners', groups.Corporate)}
    ${buildGroupSection('Other Private Owners', groups.Private)}
    ${buildGroupSection('Public Owners', groups.Public)}
    ${buildSummarySection()}
    ${buildAbsenteeSection()}
  `;
}


function generateCountySummaryHTML(props) {
  return `
    <h4>County Data Summary</h4>
    <ul>
      <li>Total parcels: ${props.parcel_count?.toLocaleString() ?? 'N/A'}</li>
      <li>Total acres: ${props.total_acres_x?.toLocaleString() ?? 'N/A'}</li>
      <li>Unieque owners: ${props.unique_owners?.toLocaleString() ?? 'N/A'}</li>
      <li>Percentage of parcels with owner details available: ${props.pct_owner_known?.toLocaleString() ?? 'N/A'}</li>
    </ul>
  `;
}


export function updateSidebar(props, ownerData, aliasMap, metrics, currentMetricId, viewMode) {
  const sidebarContent = document.getElementById('sidebar-content');

  // Normalize and pad GEOID
  const rawGeoid = props.geoid || props.GEOID || props.GEOID10 || '';
  const geoid = rawGeoid.toString().padStart(5, '0');

  const countyName = props.NAME;
  const stateAbbr = (props.filename || '').split('_')[0]?.toUpperCase() || '??';
  const metricValue = parseFloat(props[currentMetricId] || 0).toFixed(3);
  const metricName = metrics.find(m => m.id === currentMetricId)?.name || 'Metric';

  let html = `<h3>${countyName}, ${stateAbbr}</h3>`;
  html += `<p><strong>${metricName}</strong>: <span style="color: yellow;">${metricValue}</span></p>`;

  if (viewMode === 'top20') {
    const owners = ownerData[geoid] || [];
    const grouped = groupOwners(owners, aliasMap);
    html += `<h4>Top owners, total acres, and percent of county owned:</h4><ul>`;
    grouped.forEach(owner => {
      const acres = Math.round(owner.ll_gisacre).toLocaleString();
      const percent = owner.percent_total.toFixed(2);
      let originalList = '';

      if (owner.originalNames.length > 1) {
        const joinedNames = owner.originalNames.join(', ');
        originalList = `
          <details style="margin-top: 4px;">
            <summary style="cursor: pointer; font-size: 0.85em; color: #555;">Includes similar names</summary>
            <small>${joinedNames}</small>
          </details>
        `;
      }

      html += `
        <li>
          <strong>${toTitleCase(owner.name)}</strong>: ${acres} acres (${percent}%)
          ${originalList}
        </li>
      `;
    });


    html += `</ul>`;
  } else if (viewMode === 'top5corp') {
    const corpOwners = corpOwnerData[geoid] || [];
    html += generateTopCorpOwnersHTML(corpOwners);
  } else if (viewMode === 'breakdown') {
    html += generateOwnerTypeBreakdownHTML(props);
  } else if (viewMode === 'summary') {
    html += generateCountySummaryHTML(props);
  }

  sidebarContent.innerHTML = html;
}



export function showTooltip(e, map, metrics, currentMetricId) {
  const tooltip = document.getElementById('tooltip');
  const props = e.features[0].properties;
  const countyName = props.NAME;
  const stateAbbr = (props.filename || '').split('_')[0]?.toUpperCase() || '??';
  const geoid = props.geoid;
  const metricValue = parseFloat(props[currentMetricId] || 0).toFixed(3);
  const metricName = metrics.find(m => m.id === currentMetricId)?.name || 'Metric';

  map.setFilter('hover-glow', ['==', 'geoid', geoid]);

  tooltip.style.left = e.point.x + 15 + 'px';
  tooltip.style.top = e.point.y + 'px';
  tooltip.innerHTML = `<strong>${countyName}, ${stateAbbr}</strong><br/>${metricName}: ${metricValue}`;
  tooltip.style.display = 'block';
}

export function hideTooltip(map) {
  const tooltip = document.getElementById('tooltip');
  map.setFilter('hover-glow', ['==', 'geoid', '']);
  tooltip.style.display = 'none';
}
