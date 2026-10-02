const checks = [...document.querySelectorAll('.compare-check')];
const panel = document.querySelector('#comparison');
checks.forEach(check => check.addEventListener('change', () => {
  const selected = checks.filter(c => c.checked);
  if (selected.length > 2) { check.checked = false; return; }
  panel.hidden = selected.length === 0;
  if (selected.length === 1) panel.textContent = `Selected ${selected[0].dataset.place}. Choose one more area to compare.`;
  if (selected.length === 2) {
    const [a,b] = selected;
    const difference = Number(a.dataset.rate) - Number(b.dataset.rate);
    panel.textContent = `${a.dataset.place}: ${a.dataset.rate}% · ${b.dataset.place}: ${b.dataset.rate}% — a ${Math.abs(difference).toFixed(1)} percentage-point difference. Descriptive comparison; statistical significance has not been evaluated.`;
  }
}));
