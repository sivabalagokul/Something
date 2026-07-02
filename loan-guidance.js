/* ==========================================================================
   Loan Guidance & Recommendations — page interactions
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {

  /* ---------------------------------------------------------------------
     1. Guided steps — click a step to mark it (and prior steps) complete
  --------------------------------------------------------------------- */
  const steps = Array.from(document.querySelectorAll('.step'));

  steps.forEach((step, index) => {
    step.style.cursor = 'pointer';
    step.addEventListener('click', () => setActiveStep(index));
  });

  function setActiveStep(activeIndex) {
    steps.forEach((step, i) => {
      const numberEl = step.querySelector('.step-number');
      const iconEl = step.querySelector('.step-icon');
      if (!numberEl || !iconEl) return;

      if (i < activeIndex) {
        // completed
        numberEl.style.background = '#0d7a6f';
        iconEl.style.borderColor = '#0d7a6f';
        iconEl.style.background = '#e6f4f2';
      } else if (i === activeIndex) {
        // current
        numberEl.style.background = '#b8790a';
        iconEl.style.borderColor = '#b8790a';
        iconEl.style.background = '#fbf1e0';
      } else {
        // upcoming
        numberEl.style.background = '#0f2942';
        iconEl.style.borderColor = '#e2e6ea';
        iconEl.style.background = '#fafcfd';
      }
    });
  }

  /* ---------------------------------------------------------------------
     2. Start Guidance button — walks through the steps, then confirms
  --------------------------------------------------------------------- */
  const startBtn = document.querySelector('.primary-btn');
  if (startBtn) {
    startBtn.addEventListener('click', () => {
      let i = 0;
      startBtn.disabled = true;
      startBtn.textContent = 'Starting...';

      const interval = setInterval(() => {
        setActiveStep(i);
        i++;
        if (i > steps.length) {
          clearInterval(interval);
          startBtn.disabled = false;
          startBtn.textContent = 'Start Guidance';
          showToast('Guidance flow complete. Redirecting to eligibility check...');
        }
      }, 500);
    });
  }

  /* ---------------------------------------------------------------------
     3. Loan type cards — "Learn More" links
  --------------------------------------------------------------------- */
  document.querySelectorAll('.learn-more').forEach((link) => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const card = link.closest('.loan-card');
      const title = card?.querySelector('h3')?.textContent?.trim() || 'this loan';
      showToast(`Loading more details about ${title}...`);
    });
  });

  /* ---------------------------------------------------------------------
     4. Loan cards — clicking anywhere on the card also triggers Learn More
  --------------------------------------------------------------------- */
  document.querySelectorAll('.loan-card').forEach((card) => {
    card.style.cursor = 'pointer';
    card.addEventListener('click', (e) => {
      // avoid double-trigger when the Learn More link itself was clicked
      if (e.target.closest('.learn-more')) return;
      card.querySelector('.learn-more')?.click();
    });
  });

  /* ---------------------------------------------------------------------
     5. "View All Loans" link
  --------------------------------------------------------------------- */
  const viewAll = document.querySelector('.view-all');
  if (viewAll) {
    viewAll.addEventListener('click', (e) => {
      e.preventDefault();
      showToast('Opening full list of loan products...');
    });
  }

  /* ---------------------------------------------------------------------
     6. Notification / message icon buttons
  --------------------------------------------------------------------- */
  document.querySelectorAll('.icon-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const label = btn.getAttribute('aria-label') || 'Notifications';
      showToast(`${label}: nothing new right now.`);
    });
  });

  /* ---------------------------------------------------------------------
     7. Sidebar nav item active state
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
