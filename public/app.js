// State Management
const state = {
  complaints: [],
  stats: {},
  isAdmin: false,
  activeComplaint: null,
  historyFilter: 'all'
};

// DOM Query Helpers
const $ = selector => document.querySelector(selector);
const $$ = selector => document.querySelectorAll(selector);

document.addEventListener('DOMContentLoaded', () => {
  initGreeting();
  setupNavigation();
  setupRoleToggle();
  setupFilters();
  setupHistoryTabs();
  setupFormSimulator();
  setupComplaintSubmission();
  setupModalEvents();
  setupExport();

  // Initial Fetch
  refreshAllData();
});

// 1. GREETING & DATE
function initGreeting() {
  const hour = new Date().getHours();
  let timeGreeting = "Good afternoon";
  if (hour < 12) timeGreeting = "Good morning";
  else if (hour >= 18) timeGreeting = "Good evening";

  const greetingEl = $('#greetingHeading');
  if (greetingEl) {
    greetingEl.textContent = `${timeGreeting}, Aryan`;
  }

  const dateEl = $('#liveDateChip');
  if (dateEl) {
    dateEl.textContent = new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(new Date());
  }
}

// 2. NAVIGATION & TABS
function setupNavigation() {
  $$('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      showView(btn.dataset.view);
    });
  });

  $$('[data-view-link]').forEach(btn => {
    btn.addEventListener('click', () => {
      showView(btn.dataset.viewLink);
    });
  });

  const btnRaiseTop = $('#btnRaiseTop');
  if (btnRaiseTop) {
    btnRaiseTop.addEventListener('click', () => showView('submit'));
  }

  const menuToggle = $('#menuToggle');
  if (menuToggle) {
    menuToggle.addEventListener('click', () => {
      $('.sidebar').classList.toggle('open');
    });
  }
}

function showView(viewName) {
  $$('.view').forEach(v => v.classList.remove('active'));
  $$('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.view === viewName));

  const targetView = $(`#${viewName}View`);
  if (targetView) {
    targetView.classList.add('active');
  }

  $('.sidebar')?.classList.remove('open');
  window.scrollTo({ top: 0, behavior: 'smooth' });

  if (viewName === 'complaints') {
    fetchComplaints();
  }
  if (viewName === 'history') {
    renderHistoryTimeline();
  }
}

// 3. ROLE TOGGLE (STUDENT <-> ADMIN)
function setupRoleToggle() {
  const toggle = $('#roleToggle');
  const studentLbl = $('#roleLabelStudent');
  const adminLbl = $('#roleLabelAdmin');
  const userSub = $('#userSub');

  if (toggle) {
    toggle.addEventListener('change', e => {
      state.isAdmin = e.target.checked;
      studentLbl.classList.toggle('active', !state.isAdmin);
      adminLbl.classList.toggle('active', state.isAdmin);

      if (userSub) {
        userSub.textContent = state.isAdmin ? "College Administrator" : "Student · AI & DS";
      }

      showToast(state.isAdmin ? "Switched to Administrator Mode (Full Access)" : "Switched to Student Mode");
      renderComplaintsTable();

      // If modal is currently open, refresh its admin view
      if (state.activeComplaint) {
        updateModalAdminControls();
      }
    });
    studentLbl.classList.add('active');
  }
}

// 4. API FETCHING & DATA REFRESH
async function refreshAllData() {
  await Promise.all([fetchStats(), fetchComplaints()]);
  renderPriorityQueue();
  renderCategoryBars();
  renderHistoryTimeline();
}

async function fetchStats() {
  try {
    const data = await apiRequest('/api/stats');
    state.stats = data;

    $('#metricTotal').textContent = data.total ?? 0;
    $('#metricOpen').textContent = data.open ?? 0;
    $('#metricUrgent').textContent = (data.critical ?? 0) + (data.high ?? 0);
    $('#metricResolved').textContent = data.resolved ?? 0;
    $('#metricRate').textContent = `${data.resolutionRate ?? 0}% resolution rate`;
    $('#navOpenCount').textContent = data.open ?? 0;
  } catch (err) {
    console.error("Failed to load statistics:", err);
  }
}

async function fetchComplaints() {
  try {
    const search = encodeURIComponent($('#filterSearch')?.value || '');
    const status = encodeURIComponent($('#filterStatus')?.value || 'All');
    const category = encodeURIComponent($('#filterCategory')?.value || 'All');
    const sortBy = encodeURIComponent($('#filterSort')?.value || 'priority');

    const list = await apiRequest(`/api/complaints?search=${search}&status=${status}&category=${category}&sortBy=${sortBy}`);
    state.complaints = list;
    renderComplaintsTable();
    return list;
  } catch (err) {
    console.error("Failed to load complaints:", err);
    return [];
  }
}

// 5. RENDER PRIORITY QUEUE (Overview Dashboard)
function renderPriorityQueue() {
  const container = $('#priorityQueueList');
  if (!container) return;

  if (!state.complaints || state.complaints.length === 0) {
    container.innerHTML = `<div class="loading-state">No complaints logged in campus yet.</div>`;
    return;
  }

  // Show top 5 sorted by priority
  const topComplaints = state.complaints.slice(0, 5);
  container.innerHTML = topComplaints.map(c => `
    <article class="priority-row-item" onclick="openComplaintModal(${c.id})">
      <div class="item-left">
        <div class="cat-avatar">${escapeHtml(c.category.slice(0, 2).toUpperCase())}</div>
        <div class="item-content">
          <h4>${escapeHtml(c.title)}</h4>
          <div class="item-meta">
            <span>${escapeHtml(c.location)}</span>
            <span>${escapeHtml(c.status)}</span>
            <span>${escapeHtml(c.formattedCode)}</span>
            <span>${c.upvotes} 👍</span>
          </div>
        </div>
      </div>
      <div class="item-right">
        <span class="badge-priority ${c.priority.toLowerCase()}">${c.priority} · ${c.priorityScore}</span>
      </div>
    </article>
  `).join('');
}

// 6. RENDER CATEGORY BARS (Overview Dashboard)
function renderCategoryBars() {
  const container = $('#categoryBarsWrap');
  if (!container) return;

  const counts = state.stats?.categoryCounts || {};
  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);

  if (entries.length === 0) {
    container.innerHTML = `<div class="loading-state">No category metrics available.</div>`;
    return;
  }

  const maxVal = Math.max(1, ...entries.map(e => e[1]));

  container.innerHTML = entries.slice(0, 5).map(([category, count]) => {
    const percent = Math.round((count / maxVal) * 100);
    return `
      <div class="bar-row">
        <div class="bar-header">
          <span>${escapeHtml(category)}</span>
          <span style="color: var(--text-muted);">${count} report${count === 1 ? '' : 's'}</span>
        </div>
        <div class="bar-track">
          <div class="bar-fill" style="width: ${percent}%;"></div>
        </div>
      </div>
    `;
  }).join('');
}

// 7. RENDER COMPLAINTS TABLE (Register View)
function renderComplaintsTable() {
  const tbody = $('#complaintsTableBody');
  const emptyState = $('#emptyState');
  if (!tbody) return;

  if (!state.complaints || state.complaints.length === 0) {
    tbody.innerHTML = '';
    if (emptyState) emptyState.hidden = false;
    return;
  }

  if (emptyState) emptyState.hidden = true;

  tbody.innerHTML = state.complaints.map(c => {
    const statusClass = c.status.toLowerCase().replace(/\s+/g, '-');
    return `
      <tr>
        <td class="cell-complaint">
          <strong>${escapeHtml(c.title)}</strong>
          <small>${escapeHtml(c.studentName)} (${escapeHtml(c.rollNumber)}) · ${escapeHtml(c.formattedCode)} · ${escapeHtml(c.location)}</small>
        </td>
        <td>
          <span style="font-weight: 500;">${escapeHtml(c.category)}</span>
        </td>
        <td>
          <span class="badge-priority ${c.priority.toLowerCase()}">${c.priority} · ${c.priorityScore}</span>
        </td>
        <td>
          <span style="font-size: 12px; color: var(--text-muted);">${escapeHtml(c.sla)}</span>
        </td>
        <td>
          <span class="status-chip ${statusClass}">${escapeHtml(c.status)}</span>
        </td>
        <td>
          <button class="btn-upvote-inline" onclick="handleInlineUpvote(event, ${c.id})" title="Click to endorse this complaint">
            👍 <span>${c.upvotes}</span>
          </button>
        </td>
        <td class="col-actions">
          <div class="table-actions-cell">
            <button class="btn-inspect" onclick="openComplaintModal(${c.id})">Details</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

// 8. FILTERS & SEARCH
function setupFilters() {
  const searchInput = $('#filterSearch');
  const statusFilter = $('#filterStatus');
  const categoryFilter = $('#filterCategory');
  const sortFilter = $('#filterSort');

  if (searchInput) searchInput.addEventListener('input', debounce(fetchComplaints, 300));
  if (statusFilter) statusFilter.addEventListener('change', fetchComplaints);
  if (categoryFilter) categoryFilter.addEventListener('change', fetchComplaints);
  if (sortFilter) sortFilter.addEventListener('change', fetchComplaints);
}

// 9. LIVE FORM PRIORITY SIMULATOR
function setupFormSimulator() {
  const form = $('#complaintForm');
  if (!form) return;

  const categorySelect = $('#selectCategory');
  const urgencyRadios = form.querySelectorAll('input[name="urgency"]');

  const updateSimulator = () => {
    const urgency = parseInt(form.querySelector('input[name="urgency"]:checked')?.value || '2', 10);
    const category = categorySelect?.value || 'General';

    // Calculation matching Java PriorityEngine:
    let urgencyPts = 12;
    if (urgency === 1) urgencyPts = 5;
    else if (urgency === 2) urgencyPts = 12;
    else if (urgency === 3) urgencyPts = 22;
    else if (urgency === 4) urgencyPts = 32;

    let catPts = 0;
    const catLower = category.toLowerCase();
    if (catLower === 'electricity' || catLower === 'security') catPts = 3;
    else if (catLower === 'water' || catLower === 'equipment') catPts = 2;
    else if (catLower === 'wi-fi' || catLower === 'cleanliness') catPts = 1;

    const estimatedScore = urgencyPts + catPts;

    let band = "Low";
    let sla = "Within 7 Days";
    if (estimatedScore >= 35) { band = "Critical"; sla = "Within 4 Hours"; }
    else if (estimatedScore >= 25) { band = "High"; sla = "Within 24 Hours"; }
    else if (estimatedScore >= 15) { band = "Medium"; sla = "Within 3 Days"; }

    let dept = "Campus Maintenance";
    if (catLower === 'electricity') dept = "Electrical Department";
    else if (catLower === 'water') dept = "Estate & Plumbing Cell";
    else if (catLower === 'wi-fi') dept = "IT & Network Infrastructure";
    else if (catLower === 'equipment') dept = "Academic Labs & AV Support";
    else if (catLower === 'cleanliness') dept = "Housekeeping & Sanitation";
    else if (catLower === 'hostel') dept = "Hostel Administration";
    else if (catLower === 'canteen') dept = "Food & Hospitality Committee";
    else if (catLower === 'security') dept = "Campus Safety & Vigilance";
    else if (catLower === 'transport') dept = "Transport Operations";

    $('#previewScore').textContent = estimatedScore;
    $('#previewBadge').textContent = band;
    $('#previewBadge').className = `badge-priority-preview ${band.toLowerCase()}`;
    $('#previewSla').textContent = `Target SLA: ${sla}`;
    $('#previewUrgencyPts').textContent = `${urgencyPts} pts`;
    $('#previewCatPts').textContent = `${catPts} pts`;
    $('#previewDept').textContent = dept;
  };

  categorySelect?.addEventListener('change', updateSimulator);
  urgencyRadios.forEach(r => r.addEventListener('change', updateSimulator));
}

// 10. COMPLAINT SUBMISSION
function setupComplaintSubmission() {
  const form = $('#complaintForm');
  if (!form) return;

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const btn = $('#btnSubmitComplaint');
    btn.disabled = true;
    btn.innerHTML = `<span>Submitting to Java Server...</span>`;

    const formData = new FormData(form);
    const payload = Object.fromEntries(formData.entries());
    payload.urgency = parseInt(payload.urgency, 10);

    try {
      const created = await apiRequest('/api/complaints', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      showToast(`Complaint ${created.formattedCode} registered! Priority: ${created.priority} (${created.priorityScore} pts)`);
      form.reset();

      // Reset default user details
      $('#inputStudentName').value = 'Aryan Agnihotri';
      $('#inputRollNumber').value = '2510003121';

      await refreshAllData();
      showView('complaints');
    } catch (err) {
      showToast(err.message || 'Submission error occurred');
    } finally {
      btn.disabled = false;
      btn.innerHTML = `
        <span>Submit Complaint &amp; Calculate Priority</span>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
      `;
    }
  });
}

// 11. MODAL DIALOG & ADMIN ACTIONS
function setupModalEvents() {
  const modal = $('#complaintModal');
  const closeBtn = $('#modalCloseBtn');
  const footerClose = $('#modalFooterClose');
  const modalUpvote = $('#btnModalUpvote');
  const saveAdminBtn = $('#btnSaveAdminAction');
  const deleteBtn = $('#btnDeleteComplaint');

  const closeModal = () => {
    modal.hidden = true;
    state.activeComplaint = null;
  };

  closeBtn?.addEventListener('click', closeModal);
  footerClose?.addEventListener('click', closeModal);
  modal?.addEventListener('click', e => {
    if (e.target === modal) closeModal();
  });

  modalUpvote?.addEventListener('click', async () => {
    if (!state.activeComplaint) return;
    try {
      const updated = await apiRequest(`/api/complaints/${state.activeComplaint.id}/upvote`, { method: 'POST' });
      state.activeComplaint = updated;
      $('#modalUpvotes').textContent = `${updated.upvotes} Students Endorsed ("Me Too!")`;
      showToast(`Endorsed! Priority score increased to ${updated.priorityScore}`);
      await refreshAllData();
      openComplaintModal(updated.id);
    } catch (err) {
      showToast(err.message);
    }
  });

  saveAdminBtn?.addEventListener('click', async () => {
    if (!state.activeComplaint) return;
    const newStatus = $('#adminStatusSelect').value;
    const newDept = $('#adminDeptInput').value;
    const newRemark = $('#adminRemarkInput').value;

    try {
      const updated = await apiRequest(`/api/complaints/${state.activeComplaint.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: newStatus,
          department: newDept,
          adminRemark: newRemark
        })
      });

      showToast(`Complaint #${updated.id} status updated to ${updated.status}`);
      await refreshAllData();
      openComplaintModal(updated.id);
    } catch (err) {
      showToast(err.message);
    }
  });

  deleteBtn?.addEventListener('click', async () => {
    if (!state.activeComplaint) return;
    if (!confirm(`Are you sure you want to delete ${state.activeComplaint.formattedCode}?`)) return;

    try {
      await apiRequest(`/api/complaints/${state.activeComplaint.id}`, { method: 'DELETE' });
      showToast(`Complaint record removed`);
      closeModal();
      await refreshAllData();
    } catch (err) {
      showToast(err.message);
    }
  });
}

function openComplaintModal(complaintId) {
  const complaint = state.complaints.find(c => c.id === complaintId);
  if (!complaint) return;

  state.activeComplaint = complaint;

  $('#modalCode').textContent = complaint.formattedCode;
  $('#modalTitle').textContent = complaint.title;
  $('#modalPriorityBadge').textContent = `${complaint.priority} · ${complaint.priorityScore}`;
  $('#modalPriorityBadge').className = `badge-priority ${complaint.priority.toLowerCase()}`;
  $('#modalStatusBadge').textContent = complaint.status;
  $('#modalStatusBadge').className = `status-chip ${complaint.status.toLowerCase().replace(/\s+/g, '-')}`;
  $('#modalSla').textContent = complaint.sla;
  $('#modalDescription').textContent = complaint.description;

  $('#modalStudentName').textContent = complaint.studentName;
  $('#modalRollNumber').textContent = `Roll No: ${complaint.rollNumber}`;
  $('#modalLocation').textContent = complaint.location;
  $('#modalCategory').textContent = `Category: ${complaint.category}`;
  $('#modalDepartment').textContent = complaint.department;
  $('#modalUpvotes').textContent = `${complaint.upvotes} Students Endorsed ("Me Too!")`;
  $('#modalAdminRemark').textContent = complaint.adminRemark || 'No administrative remark recorded yet.';

  updateModalAdminControls();
  updateLifecycleStepper(complaint.status);

  $('#complaintModal').hidden = false;
}

function updateModalAdminControls() {
  const adminControls = $('#modalAdminControls');
  if (!adminControls) return;

  if (state.isAdmin) {
    adminControls.style.display = 'block';
    if (state.activeComplaint) {
      $('#adminStatusSelect').value = state.activeComplaint.status;
      $('#adminDeptInput').value = state.activeComplaint.department;
      $('#adminRemarkInput').value = state.activeComplaint.adminRemark;
    }
  } else {
    adminControls.style.display = 'none';
  }
}

// 12. INLINE UPVOTING
async function handleInlineUpvote(event, complaintId) {
  event.stopPropagation();
  try {
    const updated = await apiRequest(`/api/complaints/${complaintId}/upvote`, { method: 'POST' });
    showToast(`Endorsed! Priority updated to ${updated.priorityScore}`);
    await refreshAllData();
  } catch (err) {
    showToast(err.message);
  }
}

// 13. CSV EXPORT
function setupExport() {
  const btn = $('#btnExportCsv');
  if (btn) {
    btn.addEventListener('click', () => {
      window.location.href = '/api/export';
      showToast("Downloading complaints CSV report...");
    });
  }
}

// 14. HELPER UTILITIES
async function apiRequest(endpoint, options = {}) {
  const res = await fetch(endpoint, options);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || 'Server request failed');
  }
  return data;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/[&<>'"]/g, tag => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;'
  }[tag]));
}

function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

let toastTimer;
function showToast(message) {
  const toast = $('#toastNotification');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.classList.remove('show');
  }, 3200);
}

function formatDate(val) {
  if (!val) return 'Recently';
  try {
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(val));
  } catch (e) {
    return String(val);
  }
}

// 15. LIFECYCLE STEPPER (Modal Progress)
function updateLifecycleStepper(status) {
  const steps = [
    { id: 'step-submitted', name: 'Submitted' },
    { id: 'step-under-review', name: 'Under Review' },
    { id: 'step-assigned', name: 'Assigned' },
    { id: 'step-in-progress', name: 'In Progress' },
    { id: 'step-resolved', name: 'Resolved' }
  ];

  let currentIdx = steps.findIndex(s => s.name.toLowerCase() === (status || '').toLowerCase());
  if (currentIdx === -1) currentIdx = 0;

  steps.forEach((step, idx) => {
    const el = $(`#${step.id}`);
    if (!el) return;
    el.classList.remove('completed', 'active');
    if (idx < currentIdx) {
      el.classList.add('completed');
    } else if (idx === currentIdx) {
      el.classList.add('active');
    }
  });

  for (let i = 1; i <= 4; i++) {
    const conn = $(`#conn-${i}`);
    if (conn) {
      conn.classList.toggle('completed', i <= currentIdx);
    }
  }
}

// 16. HISTORY & AUDIT LOG FUNCTIONS
function setupHistoryTabs() {
  $$('.hist-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      $$('.hist-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      state.historyFilter = tab.dataset.histFilter || 'all';
      renderHistoryTimeline();
    });
  });

  const histSearch = $('#histSearchInput');
  if (histSearch) {
    histSearch.addEventListener('input', debounce(renderHistoryTimeline, 250));
  }
}

function renderHistoryTimeline() {
  const container = $('#historyTimelineContainer');
  if (!container) return;

  const resolvedCount = state.complaints.filter(c => c.status === 'Resolved').length;
  const myCount = state.complaints.filter(c => c.studentName.toLowerCase().includes('aryan')).length;

  const countResolvedEl = $('#histResolvedCount');
  const countMyEl = $('#histMyCount');
  if (countResolvedEl) countResolvedEl.textContent = resolvedCount;
  if (countMyEl) countMyEl.textContent = myCount;

  const search = ($('#histSearchInput')?.value || '').toLowerCase().trim();
  let list = [...state.complaints];

  if (state.historyFilter === 'mine') {
    list = list.filter(c => c.studentName.toLowerCase().includes('aryan'));
  } else if (state.historyFilter === 'resolved') {
    list = list.filter(c => c.status === 'Resolved');
  } else if (state.historyFilter === 'urgent') {
    list = list.filter(c => c.priorityScore >= 25);
  }

  if (search) {
    list = list.filter(c => 
      (c.title + ' ' + c.studentName + ' ' + c.location + ' ' + c.department + ' ' + c.status).toLowerCase().includes(search)
    );
  }

  if (list.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="padding: 32px 16px;">
        <h4>No history records match this filter</h4>
        <p>Switch filter tabs or submit new complaints to populate audit history.</p>
      </div>
    `;
    return;
  }

  container.innerHTML = list.map(c => {
    const isResolved = c.status === 'Resolved';
    const isCritical = c.priorityScore >= 35;
    const markerClass = isResolved ? 'resolved' : (isCritical ? 'critical' : '');
    const markerIcon = isResolved ? '✓' : (isCritical ? '!' : '▤');

    return `
      <div class="timeline-event-card" onclick="openComplaintModal(${c.id})">
        <div class="timeline-marker ${markerClass}">
          <span>${markerIcon}</span>
        </div>
        <div class="timeline-content-card">
          <div class="timeline-top-row">
            <h4>${escapeHtml(c.title)}</h4>
            <span class="timeline-time">${formatDate(c.createdAt)}</span>
          </div>
          <div class="timeline-sub-row">
            <span><strong>${escapeHtml(c.formattedCode)}</strong></span>
            <span>By ${escapeHtml(c.studentName)} (${escapeHtml(c.rollNumber)})</span>
            <span>📍 ${escapeHtml(c.location)}</span>
            <span>🏛️ ${escapeHtml(c.department)}</span>
            <span>👍 ${c.upvotes} endorsements</span>
          </div>
          <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 6px;">
            <span class="badge-priority ${c.priority.toLowerCase()}">${c.priority} · ${c.priorityScore}</span>
            <span class="status-chip ${c.status.toLowerCase().replace(/\s+/g, '-')}">${escapeHtml(c.status)}</span>
            <small style="color: var(--text-muted);">${escapeHtml(c.sla)}</small>
          </div>
          ${c.adminRemark ? `<div class="timeline-remark-bubble"><strong>Resolution Audit:</strong> ${escapeHtml(c.adminRemark)}</div>` : ''}
        </div>
      </div>
    `;
  }).join('');
}
