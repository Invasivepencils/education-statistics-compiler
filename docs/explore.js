let data=[], results=[], page=1;
const form=document.querySelector('#filters'), tbody=document.querySelector('tbody');
const title=document.querySelector('#result-title'), panel=document.querySelector('#comparison');
const context=[...document.querySelectorAll('.context strong')];
const pagination=document.querySelector('.pagination');
const download=document.querySelector('#export');
const groups={all:'Total',african:'AfricanAm',asian:'Asian',latino:'Latino',white:'White',aian:'AIAN',nhopi:'NHOPI',multiple:'Multiple',other:'Other'};
const types={county:'CO',city:'PL',region:'RE',state:'CA'};
const clean=x=>['NA','N/A',''].includes(x)?'':x;
const place=r=>r[2]==='CO'?`${clean(r[4])||r[3]} County`:r[2]==='CA'?'California':r[2]==='RE'?clean(r[5])||r[3]:r[3].replace(/\s+(city|town|CDP)$/i,'');
const group=r=>({Total:'All races (Total)',AfricanAm:'African American',AIAN:'American Indian or Alaska Native',NHOPI:'Native Hawaiian or Pacific Islander'}[r[1]]||r[1]);
const normalized=s=>s.toLowerCase().replace(/\b(county|city|town)\b/g,'').trim();
const params=new URLSearchParams(location.search);
for(const name of ['query','scope','race','period','sortOrder']) if(params.has(name)){
  const field=form.elements[name];
  if(field && (field.tagName!=='SELECT'||[...field.options].some(o=>o.value===params.get(name))))field.value=params.get(name);
}
function render(){
  const f=Object.fromEntries(new FormData(form)), terms=normalized(f.query||'').split(/\s+/).filter(Boolean);
  const selectedGroup=groups[f.race]||'Total', selectedType=types[f.scope]||'CO';
  results=data.filter(r=>r[2]===selectedType&&r[1]===selectedGroup&&r[0]===f.period&&terms.every(t=>normalized(`${place(r)} ${clean(r[4])} ${clean(r[5])}`).includes(t)));
  results.sort((a,b)=>(f.sortOrder==='lowest'?a[6]-b[6]:b[6]-a[6])||place(a).localeCompare(place(b)));
  const state=data.find(r=>r[2]==='CA'&&r[1]===selectedGroup&&r[0]===f.period);
  const benchmark=state?.[6], pages=Math.max(1,Math.ceil(results.length/25));page=Math.max(1,Math.min(page,pages));
  title.textContent=`${results.length} matching areas`;context[0].textContent=f.period;context[1].textContent=benchmark==null?'Unavailable':benchmark.toFixed(1)+'%';
  panel.hidden=true;tbody.replaceChildren();
  document.querySelector('.empty')?.remove();
  if(!results.length){const tr=document.createElement('tr'),td=document.createElement('td');td.colSpan=5;td.textContent='No areas match. Try another place, geographic level, group, or period.';tr.append(td);tbody.append(tr);}
  for(const r of results.slice((page-1)*25,page*25)){
    const tr=document.createElement('tr'), cells=Array.from({length:5},()=>document.createElement('td'));
    const check=document.createElement('input');check.type='checkbox';check.className='compare-check';check.setAttribute('aria-label','Compare '+place(r));check.dataset.place=place(r);check.dataset.rate=r[6];cells[0].append(check);
    const name=document.createElement('strong'),sub=document.createElement('span');name.textContent=place(r);sub.className='sub';sub.textContent=[{CO:'County',PL:'Place',RE:'Region',CA:'State'}[r[2]],r[2]!=='CO'&&clean(r[4])?r[4]+' County':'',clean(r[5])].filter(Boolean).join(' · ');cells[1].append(name,sub);cells[2].textContent=group(r);
    const rate=document.createElement('div'),strong=document.createElement('strong'),bar=document.createElement('span'),fill=document.createElement('span');rate.className='rate';strong.textContent=r[6]+'%';bar.className='bar';fill.style.width=r[6]+'%';bar.append(fill);rate.append(strong,bar);cells[3].append(rate);
    cells[4].textContent=benchmark==null?'—':(r[6]-benchmark).toFixed(1)+' pp';tr.append(...cells);tbody.append(tr);
    check.addEventListener('change',()=>{const selected=[...tbody.querySelectorAll('input:checked')];if(selected.length>2){check.checked=false;return;}panel.hidden=!selected.length;if(selected.length===1)panel.textContent=`Selected ${selected[0].dataset.place}. Choose one more area.`;if(selected.length===2){const[a,b]=selected;panel.textContent=`${a.dataset.place}: ${a.dataset.rate}% · ${b.dataset.place}: ${b.dataset.rate}% — a ${Math.abs(a.dataset.rate-b.dataset.rate).toFixed(1)} percentage-point difference. Descriptive comparison; statistical significance has not been evaluated.`;}});
  }
  pagination.replaceChildren();const label=document.createElement('span');label.textContent=`Page ${page} of ${pages} · 25 areas per page`;pagination.append(label);
  for(const [text,delta] of [['← Previous',-1],['Next →',1]]){const b=document.createElement('button');b.type='button';b.textContent=text;b.disabled=page+delta<1||page+delta>pages;b.addEventListener('click',()=>{page+=delta;render();});pagination.append(b);}
  download.setAttribute('aria-disabled',String(!results.length));
}
form.addEventListener('submit',e=>{e.preventDefault();page=1;const p=new URLSearchParams(new FormData(form));history.replaceState(null,'','?'+p);render();});
download.addEventListener('click',e=>{e.preventDefault();if(!results.length)return;const escape=x=>'"'+String(x).replace(/^[=+@-]/,"'$&").replaceAll('"','""')+'"';const rows=[['place','geographic_level','group','reporting_period','attainment_percent','numerator','denominator'],...results.map(r=>[place(r),r[2],group(r),r[0],r[6],r[7],r[8]])];const url=URL.createObjectURL(new Blob([rows.map(r=>r.map(escape).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='attainment.csv';a.click();URL.revokeObjectURL(url);});
fetch('data.json').then(r=>{if(!r.ok)throw Error('The snapshot could not load. Reload or open the source repository.');return r.json();}).then(d=>{data=d;render();}).catch(e=>{title.textContent='Snapshot unavailable';const note=document.createElement('p');note.setAttribute('role','alert');note.textContent=e.message;title.after(note);});
