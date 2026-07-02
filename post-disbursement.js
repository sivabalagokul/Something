/* ==========================================================================
   Post Disbursement — page interactions
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {

  /* ---------------------------------------------------------------------
     1. Loan account selector (dropdown toggle + fake multi-account list)
  --------------------------------------------------------------------- */
  const accountSelect = document.querySelector('.account-select');
  if (accountSelect) {
    const accounts = [
      { id: 'HL1234567890', label: 'HL1234567890 - Home Loan' },
      { id: 'HL9988776655', label: 'HL9988776655 - Construction Loan' },
    ];

    accountSelect.style.cursor = 'pointer';
    accountSelect.setAttribute('role', 'button');
    accountSelect.setAttribute('tabindex', '0');

    const closeMenu = () => {
      const existing = document.querySelector('.account-menu');
      if (existing) existing.remove();
      document.removeEventListener('click', outsideClickHandler);
    };

    const outsideClickHandler = (e) => {
      if (!accountSelect.contains(e.target)) closeMenu();
    };

    const openMenu = () => {
      if (document.querySelector('.account-menu')) return;

      const menu = document.createElement('div');
      menu.className = 'account-menu';
      Object.assign(menu.style, {
        position: 'absolute',
        top: 'calc(100% + 6px)',
        left: '0',
        right: '0',
        background: '#fff',
        border: '1px solid #e2e6ea',
        borderRadius: '8px',
        boxShadow: '0 8px 24px rgba(15,41,66,0.12)',
        zIndex: '60',
        overflow: 'hidden',
      });

      accounts.forEach((acc) => {
        const item = document.createElement('div');
        item.textContent = acc.label;
        Object.assign(item.style, {
          padding: '10px 14px',
          fontSize: '13.5px',
          fontWeight: '600',
          color: '#0f2942',
          cursor: 'pointer',
        });
        item.addEventListener('mouseenter', () => (item.style.background = '#e6f4f2'));
        item.addEventListener('mouseleave', () => (item.style.background = '#fff'));
        item.addEventListener('click', (e) => {
          e.stopPropagation();
          const span = accountSelect.querySelector('.row span');
          if (span) span.textContent = acc.label;
          closeMenu();
        });
        menu.appendChild(item);
      });

      accountSelect.style.position = 'relative';
      accountSelect.appendChild(menu);
      document.addEventListener('click', outsideClickHandler);
    };

    accountSelect.addEventListener('click', (e) => {
      e.stopPropagation();
      if (document.querySelector('.account-menu')) {
        closeMenu();
      } else {
        openMenu();
      }
    });

    accountSelect.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        accountSelect.click();
      }
    });
  }

  /* ---------------------------------------------------------------------
     2. Quick Links — clickable rows
  --------------------------------------------------------------------- */
  document.querySelectorAll('.quick-links li').forEach((item) => {
    item.style.cursor = 'pointer';
    item.addEventListener('click', () => {
      const label = item.textContent.trim();
      showToast(`Opening "${label}"...`);
    });
  });

  /* ---------------------------------------------------------------------
     3. Ghost buttons (View Disbursement Letter / Property Docs / Insurance)
  --------------------------------------------------------------------- */
  document.querySelectorAll('.ghost-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const label = btn.textContent.trim();
      showToast(`Fetching "${label}"...`);
    });
  });

  /* ---------------------------------------------------------------------
     4. Checklist "View" links
  --------------------------------------------------------------------- */
  document.querySelectorAll('.view-link').forEach((link) => {
    link.style.cursor = 'pointer';
    link.addEventListener('click', () => {
      const row = link.closest('tr');
      const activityName = row?.querySelector('.activity-cell div div')?.textContent?.trim() || 'document';
      showToast(`Opening details for "${activityName}"...`);
    });
  });

  /* ---------------------------------------------------------------------
     5. Insurance "View All Insurance Details" already covered by .ghost-btn
     6. Document upload — drag & drop + choose files + recent uploads list
  --------------------------------------------------------------------- */
  const dropzone = document.querySelector('.dropzone');
  const chooseBtn = document.querySelector('.choose-btn');
  const recentList = document.querySelector('.recent-uploads');

  if (dropzone) {
    // Hidden file input to power "Choose Files"
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.multiple = true;
    fileInput.accept = '.pdf,.jpg,.jpeg,.png';
    fileInput.style.display = 'none';
    document.body.appendChild(fileInput);

    const highlight = (on) => {
      dropzone.style.borderColor = on ? '#0d7a6f' : '#c9d3da';
      dropzone.style.background = on ? '#e6f4f2' : '#fafcfd';
    };

    ['dragenter', 'dragover'].forEach((evt) =>
      dropzone.addEventListener(evt, (e) => {
        e.preventDefault();
        highlight(true);
      })
    );

    ['dragleave', 'drop'].forEach((evt) =>
      dropzone.addEventListener(evt, (e) => {
        e.preventDefault();
        highlight(false);
      })
    );

    dropzone.addEventListener('drop', (e) => {
      const files = Array.from(e.dataTransfer.files || []);
      handleNewFiles(files);
    });

    if (chooseBtn) {
      chooseBtn.addEventListener('click', () => fileInput.click());
    }

    fileInput.addEventListener('change', () => {
      handleNewFiles(Array.from(fileInput.files || []));
      fileInput.value = '';
    });
  }

  function handleNewFiles(files) {
    if (!files.length || !recentList) return;

    const maxBytes = 10 * 1024 * 1024;
    const validExt = /\.(pdf|jpg|jpeg|png)$/i;

    files.forEach((file) => {
      if (!validExt.test(file.name)) {
        showToast(`"${file.name}" is not a supported file type.`);
        return;
      }
      if (file.size > maxBytes) {
        showToast(`"${file.name}" exceeds the 10 MB limit.`);
        return;
      }
      addUploadRow(file.name);
    });
  }

  function addUploadRow(fileName) {
    const rowsContainer = document.querySelector('.recent-uploads');
    if (!rowsContainer) return;

    const row = document.createElement('div');
    row.className = 'upload-row';
    row.innerHTML = `
      <div class="file-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <path d="M14 2v6h6"/>
        </svg>
      </div>
      <span class="fname">${escapeHtml(fileName)}</span>
      <span class="fdate">${formatToday()}</span>
    `;

    // Insert right after the "Recent Uploads" heading row
    const heading = rowsContainer.querySelector('.recent-head');
    if (heading && heading.nextSibling) {
      heading.parentNode.insertBefore(row, heading.nextSibling);
    } else {
      rowsContainer.appendChild(row);
    }

    showToast(`"${fileName}" uploaded successfully.`);
  }

  function formatToday() {
    const d = new Date();
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return `${String(d.getDate()).padStart(2, '0')} ${months[d.getMonth()]} ${d.getFullYear()}`;
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  /* ---------------------------------------------------------------------
     7. Notification / message icon buttons
  --------------------------------------------------------------------- */
  document.querySelectorAll('.icon-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const label = btn.getAttribute('aria-label') || 'Notifications';
      showToast(`${label}: nothing new right now.`);
    });
  });

  /* ---------------------------------------------------------------------
     8. Sidebar nav item active state
  --------------------------------------------------------------------- */
  document.querySelectorAll('.nav-item').forEach((item) => {
    item.addEventListener('click', () => {
      document.querySelectorAll('.nav-item').forEach((i) => i.classList.remove('active'));
      item.classList.add('active');
    });
  });

  /* ---------------------------------------------------------------------
     Lightweight toast notification helper
  --------------------------------------------------------------------- */
  function showToast(message) {
    let container = document.querySelector('.toast-container');
    if (!container) {
      container = document.createElement('div');
      container.className = 'toast-container';
      Object.assign(container.style, {
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
        zIndex: '999',
      });
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.textContent = message;
    Object.assign(toast.style, {
      background: '#0f2942',
      color: '#fff',
      padding: '12px 18px',
      borderRadius: '8px',
      fontSize: '13px',
      fontWeight: '600',
      boxShadow: '0 8px 24px rgba(15,41,66,0.25)',
      opacity: '0',
      transform: 'translateY(8px)',
      transition: 'opacity 0.2s ease, transform 0.2s ease',
      maxWidth: '320px',
    });

    container.appendChild(toast);
    requestAnimationFrame(() => {
      toast.style.opacity = '1';
      toast.style.transform = 'translateY(0)';
    });

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(8px)';
      setTimeout(() => toast.remove(), 200);
    }, 2800);
  }

});
