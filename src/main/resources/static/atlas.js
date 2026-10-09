const checks = [...document.querySelectorAll('.compare-check')];
const panel = document.querySelector('#comparison');
checks.forEach(check => check.addEventListener('change', () => {
  const selected = checks.filter(c => c.checked);
  if (selected.length > 2) { check.checked = false; panel.textContent='Two areas are selected. Untick one before choosing another.'; return; }
  panel.hidden = false;if(!selected.length)panel.textContent='Tick two community boxes to compare their rates.';
  if (selected.length === 1) panel.textContent = `Selected ${selected[0].dataset.place}. Choose one more area to compare.`;
  if (selected.length === 2) {
    const [a,b] = selected;
    const difference = Number(a.dataset.rate) - Number(b.dataset.rate);
    panel.textContent = `${a.dataset.place}: about ${Math.round(a.dataset.rate)} in every 100 adults (${a.dataset.rate}%). ${b.dataset.place}: about ${Math.round(b.dataset.rate)} in every 100 adults (${b.dataset.rate}%). ${Math.abs(difference)<0.05?'The reported rates are the same.':(difference>0?a.dataset.place:b.dataset.place)+' has the higher reported rate by '+Math.abs(difference).toFixed(1)+' percentage points.'} Same group and period; statistical significance has not been evaluated.`;
  }
}));

if(panel){panel.hidden=false;panel.textContent='Tick the boxes beside two communities to compare their rates.';}
document.querySelectorAll('.rate strong').forEach(e=>{const rate=Number(e.textContent.replace('%',''));if(Number.isFinite(rate)){const p=document.createElement('span');p.className='sub';p.textContent='About '+Math.round(rate)+' in every 100 adults';e.after(p);}});
document.querySelectorAll('.gap').forEach(e=>{const n=parseFloat(e.textContent);if(Number.isFinite(n))e.textContent=Math.abs(n)<0.05?'Same reported rate':Math.abs(n).toFixed(1)+' points '+(n>0?'higher':'lower');});
