/* ===== Billar Control: pantalla conectada al backend (billar-api) ===== */
const API = (window.BILLAR_API || 'http://localhost:8080').replace(/\/$/, '');

/* ---------- utilidades ---------- */
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money = n => new Intl.NumberFormat('es-CO',{style:'currency',currency:'COP',maximumFractionDigits:0}).format(Math.round(n||0));
const num = v => parseInt(String(v ?? '').replace(/[^\d]/g,''),10) || 0;
const DAY = 864e5;
const fmtTime = iso => new Date(iso).toLocaleTimeString('es-CO',{hour:'numeric',minute:'2-digit'});
const fmtDT = iso => new Date(iso).toLocaleString('es-CO',{day:'numeric',month:'short',hour:'numeric',minute:'2-digit'});
const fmtHMS = ms => { const s = Math.max(0,Math.floor(ms/1000)); const p = n => String(n).padStart(2,'0'); return p(Math.floor(s/3600))+':'+p(Math.floor(s%3600/60))+':'+p(s%60); };
const ymd = d => { const p = n => String(n).padStart(2,'0'); return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate()); };
const methodLabel = {CASH:'Efectivo', TRANSFER:'Transferencia', CREDIT:'Fiado', COURTESY:'Cortesía'};

/* ---------- estado ---------- */
function store(k, v){ try { if (v === null) sessionStorage.removeItem(k); else sessionStorage.setItem(k, v); } catch(e){} }
function stored(k){ try { return sessionStorage.getItem(k); } catch(e){ return null; } }
let TOKEN = stored('bc_token') || '';
let USER = null;
try { USER = TOKEN ? JSON.parse(stored('bc_user') || 'null') : null; } catch(e){ USER = null; }
const isOwner = () => !!USER && USER.role === 'OWNER';

const D = {tables:[], products:[], sessions:[], settings:{businessName:'Billar Control', billingStepMinutes:1, baseCash:0},
           shift:null, summary:null, sales:[], audit:[], users:[]};
let tab = 'mesas', period = 'hoy', sheet = null, offset = 0, busy = false, toastT;

function toast(msg, ms = 3200){ const t = $('#toast'); t.textContent = msg; t.hidden = false; clearTimeout(toastT); toastT = setTimeout(()=>{ t.hidden = true; }, ms); }

/* ---------- conexión con el servidor ---------- */
async function api(path, method = 'GET', body){
  let res;
  try {
    res = await fetch(API + path, {
      method,
      headers: Object.assign({'Content-Type':'application/json'}, TOKEN ? {Authorization:'Bearer '+TOKEN} : {}),
      body: body !== undefined ? JSON.stringify(body) : undefined
    });
  } catch(e){ throw new Error('No hay conexión con el servidor'); }
  if (res.status === 401 && path !== '/api/auth/login'){ logout(); throw new Error('Tu sesión venció. Entra de nuevo.'); }
  let data = null;
  const txt = await res.text();
  if (txt){ try { data = JSON.parse(txt); } catch(e){} }
  if (!res.ok) throw new Error((data && data.error) || ('Error '+res.status));
  return data;
}
async function loadSettings(){ D.settings = await api('/api/settings'); document.title = D.settings.businessName; }
async function loadTables(){ D.tables = await api('/api/tables'); }
async function loadProducts(){ D.products = await api('/api/products'); }
async function loadSessions(){ D.sessions = await api('/api/sessions'); if (D.sessions.length) offset = Date.parse(D.sessions[0].serverNow) - Date.now(); }
async function loadShift(){ D.shift = await api('/api/cash/shift'); }
async function loadUsers(){ D.users = await api('/api/users'); }
async function loadReports(){
  const r = range(), q = 'from='+r.from+'&to='+r.to;
  const res = await Promise.all([api('/api/reports/summary?'+q), api('/api/sales?'+q), api('/api/audit')]);
  D.summary = res[0]; D.sales = res[1]; D.audit = res[2];
}
async function loadTab(){
  try {
    if (tab === 'mesas') await Promise.all([loadTables(), loadProducts(), loadSessions()]);
    else if (tab === 'productos') await loadProducts();
    else if (tab === 'caja') await loadShift();
    else if (tab === 'informes') await loadReports();
    else if (tab === 'ajustes') await Promise.all([loadSettings(), loadTables(), loadUsers()]);
    render();
  } catch(e){ toast(e.message); }
}
function range(){
  const now = new Date();
  const back = period === 'hoy' ? 0 : period === '7' ? 6 : 29;
  return {from: ymd(new Date(now.getTime() - back*DAY)), to: ymd(now), label: period === 'hoy' ? 'Hoy' : period === '7' ? 'Últimos 7 días' : 'Últimos 30 días'};
}

/* Solo para mostrar el estimado en pantalla. El cobro real lo calcula el servidor. */
function billedMinutes(ms){
  const m = ms/60000, step = D.settings.billingStepMinutes || 1;
  return step > 1 ? Math.max(step, Math.ceil((m - 0.0001)/step)*step) : Math.max(1, Math.ceil(m));
}
const timeValue = (ms, rate) => Math.round(billedMinutes(ms)/60*rate/50)*50;

/* ---------- vistas ---------- */
const app = $('#app');

function render(){
  if (!USER){ renderLogin(); renderSheet(); return; }
  const owner = isOwner();
  const tabs = owner
    ? [['mesas','Mesas'],['productos','Productos'],['caja','Caja'],['informes','Informes'],['ajustes','Ajustes']]
    : [['mesas','Mesas'],['productos','Productos'],['caja','Caja']];
  if (!tabs.some(t => t[0] === tab)) tab = 'mesas';
  const view = {mesas:viewMesas, productos:viewProductos, caja:viewCaja, informes:viewInformes, ajustes:viewAjustes}[tab]();
  app.innerHTML =
    `<header class="top"><div><div class="name">${esc(D.settings.businessName)}</div><div class="who">${owner?'Dueño':'Turno de '+esc(USER.fullName||USER.username)}</div></div><button data-a="logout">Salir</button></header>
     <main class="wrap">${view}</main>
     <nav class="tabs" aria-label="Secciones">${tabs.map(t => `<button data-a="tab" data-t="${t[0]}" class="${tab===t[0]?'on':''}">${t[1]}</button>`).join('')}</nav>`;
  renderSheet();
  tick();
}

function renderLogin(){
  let last = ''; try { last = localStorage.getItem('bc_last_user') || ''; } catch(e){}
  app.innerHTML =
   `<main class="login">
      <div class="brand-big">${esc(D.settings.businessName)}</div>
      <p class="muted">Mesas, consumos y caja en un solo lugar.</p>
      <section class="card"><h2>Entrar</h2>
        <label for="user">Usuario</label>
        <input id="user" value="${esc(last)}" autocomplete="username" autocapitalize="none">
        <label for="pass">Contraseña</label>
        <input id="pass" type="password" autocomplete="current-password">
        <p class="err" id="loginErr"></p>
        <div class="actions"><button class="btn primary" data-a="login">Entrar</button></div>
      </section>
    </main>`;
}

function mesaCard(t){
  const s = D.sessions.find(x => x.tableId === t.id);
  if (!s){
    return `<article class="mesa libre"><div class="mhead"><h3>${esc(t.name)}</h3><span class="rate">${money(t.ratePerHour)} por hora</span></div>
      <p class="state">Libre</p>
      <button class="btn primary" data-a="start" data-id="${t.id}">Iniciar mesa</button></article>`;
  }
  const n = s.items.reduce((a,i) => a + i.qty, 0);
  return `<article class="mesa ocupada"><div class="mhead"><h3>${esc(t.name)}</h3><span class="rate">${money(t.ratePerHour)} por hora</span></div>
    <div class="clock" data-timer data-start="${esc(s.startedAt)}">00:00:00</div>
    <p class="sub">Inició a las ${fmtTime(s.startedAt)}. Tiempo hasta ahora: <b data-est data-start="${esc(s.startedAt)}" data-rate="${s.ratePerHour}"></b></p>
    <p class="sub">Consumos: ${n} producto${n===1?'':'s'}, ${money(s.itemsTotal)}</p>
    <div class="btns"><button class="btn" data-a="items" data-id="${s.id}">Consumos</button><button class="btn primary" data-a="charge" data-id="${s.id}">Cobrar</button></div></article>`;
}

function viewMesas(){
  return `<div class="sectionhead"><h2>Mesas</h2><button class="btn small" data-a="venta">Venta rápida</button></div>
    ${D.tables.length ? `<div class="grid">${D.tables.map(mesaCard).join('')}</div>` : '<p class="muted">Cargando mesas…</p>'}
    <div class="sectionhead"><h2>Cuentas abiertas</h2><button class="btn small" data-a="newtab">Nueva cuenta</button></div>
    <div class="card"><ul class="lines">${D.sessions.filter(s => s.tableId === undefined).map(s => `<li><div class="grow"><b>${esc(s.tableName)}</b><div class="muted">Desde las ${fmtTime(s.startedAt)}, ${money(s.itemsTotal)}</div></div><button class="btn small" data-a="items" data-id="${s.id}">Consumos</button><button class="btn small primary" data-a="charge" data-id="${s.id}">Cobrar</button></li>`).join('') || '<li class="empty">Sin cuentas abiertas.</li>'}</ul></div>`;
}

function viewProductos(){
  const owner = isOwner();
  const rows = D.products.map(p => {
    const tag = p.stock <= 0 ? '<span class="tag warn">Agotado</span>' : p.stock <= 3 ? '<span class="tag ball">Quedan pocos</span>' : '';
    return `<li ${owner?`data-a="prod" data-id="${p.id}" style="cursor:pointer"`:''}><div class="grow"><b>${esc(p.name)}</b> ${tag}<div class="muted">${money(p.price)}${owner&&p.cost!==undefined?', costo '+money(p.cost):''}</div></div><div class="big">${p.stock}</div></li>`;
  }).join('') || '<li class="empty">No hay productos todavía.</li>';
  const inv = owner ? D.products.reduce((a,p) => a + p.stock*(p.cost||0), 0) : 0;
  return `<div class="sectionhead"><h2>Productos</h2><button class="btn small" data-a="venta">Venta rápida</button></div>
    <div class="actions" style="margin-top:0"><button class="btn" data-a="count">Contar inventario</button>${owner?'<button class="btn primary" data-a="prod" data-id="new">Nuevo producto</button>':''}</div>
    <div class="card"><ul class="lines">${rows}</ul></div>
    ${owner?`<p class="muted">Valor del inventario al costo: <b>${money(inv)}</b>. Toca un producto para editarlo o registrar una compra.</p>`:''}`;
}

function viewCaja(){
  const sh = D.shift;
  if (!sh) return '<div class="sectionhead"><h2>Caja</h2></div><p class="muted">Cargando…</p>';
  const exps = sh.expenses.map(e => `<li><div class="grow">${esc(e.concept)}<div class="muted">${fmtDT(e.createdAt)}, ${esc(e.createdBy)}</div></div><b>${money(e.amount)}</b></li>`).join('') || '<li class="empty">Sin gastos en este turno.</li>';
  const full = isOwner() && sh.expectedCash !== undefined;
  const ownerBox = full ? `
    <div class="card"><h2>Efectivo esperado</h2>
      <div class="row"><span>Base de caja</span><span>${money(sh.baseCash)}</span></div>
      <div class="row"><span>Ventas en efectivo</span><span>${money(sh.cash)}</span></div>
      <div class="row"><span>Gastos</span><span>− ${money(sh.expenses.reduce((a,e)=>a+e.amount,0))}</span></div>
      <div class="row total"><span>En caja debería haber</span><b>${money(sh.expectedCash)}</b></div>
    </div>
    <div class="card"><h2>Otros medios</h2>
      <div class="row"><span>Transferencias</span><span>${money(sh.transfer)}</span></div>
      <div class="row"><span>Fiado nuevo</span><span>${money(sh.creditNew)}</span></div>
      <div class="row"><span>Cortesías</span><span>${money(sh.courtesy)}</span></div>
    </div>` : `<p class="muted">Cuenta el dinero de la caja y regístralo al cerrar. El sistema compara tu conteo con lo vendido.</p>`;
  return `<div class="sectionhead"><h2>Caja</h2></div>
    <div class="card"><div class="row"><span>Turno abierto desde</span><b>${sh.from && Date.parse(sh.from) > 86400000 ? fmtDT(sh.from) : 'el inicio'}</b></div>
      <div class="row"><span>Ventas registradas</span><b>${sh.salesCount}</b></div>
      <div class="row"><span>Mesas abiertas ahora</span><b>${sh.openTables}</b></div></div>
    ${ownerBox}
    <div class="sectionhead"><h2>Gastos del turno</h2><button class="btn small" data-a="expense">Registrar gasto</button></div>
    <div class="card"><ul class="lines">${exps}</ul></div>
    <div class="actions"><button class="btn primary" data-a="closeshift">Cerrar turno</button></div>`;
}

function viewInformes(){
  const R = D.summary;
  if (!R) return '<div class="sectionhead"><h2>Informes</h2></div><p class="muted">Cargando…</p>';
  const st = R.stats;
  const horas = (st.minutes/60).toFixed(1);
  const days = R.daily.slice(-7).map(d => ({l:new Date(d.date+'T12:00:00').toLocaleDateString('es-CO',{weekday:'short'}), v:d.income}));
  const mx = Math.max(1, ...days.map(d => d.v));
  const bars = days.map(d => `<div class="bar"><span>${d.v?Math.round(d.v/1000)+'k':''}</span><div class="fill" style="height:${Math.round(d.v/mx*80)}%"></div><span>${esc(d.l)}</span></div>`).join('');

  const al = [];
  R.cashDifferences.forEach(c => al.push(`<li><div class="grow">Cierre del ${fmtDT(c.closedAt)} (${esc(c.closedBy)})<div class="muted">Esperado ${money(c.expectedCash)}, contado ${money(c.countedCash)}</div></div><b class="${c.difference<0?'neg':'pos'}">${money(c.difference)}</b></li>`));
  if (R.inventoryDifferenceAtCost) al.push(`<li><div class="grow">Diferencias de inventario<div class="muted">Valoradas al costo</div></div><b class="${R.inventoryDifferenceAtCost<0?'neg':'pos'}">${money(R.inventoryDifferenceAtCost)}</b></li>`);
  if (R.shortSessions) al.push(`<li><div class="grow">Sesiones de menos de 5 minutos<div class="muted">Revisa si el cobro fue correcto</div></div><b>${R.shortSessions}</b></li>`);
  if (st.courtesy) al.push(`<li><div class="grow">Cortesías entregadas</div><b>${money(st.courtesy)}</b></li>`);
  if (R.voidedSales) al.push(`<li><div class="grow">Ventas anuladas</div><b>${R.voidedSales}</b></li>`);

  const fiados = R.creditPending.map(s => `<li><div class="grow"><b>${esc(s.customerName)}</b><div class="muted">${fmtDT(s.createdAt)}, ${esc(s.tableName)}</div></div><b>${money(s.total)}</b><button class="btn small" data-a="paid" data-id="${s.id}">Cobrar</button></li>`).join('') || '<li class="empty">Nadie debe nada.</li>';

  const ventas = D.sales.slice(0,30).map(s => {
    const voided = s.status === 'VOIDED';
    const min = s.type === 'TABLE' ? s.billedMinutes+' min' : 'Venta rápida';
    return `<li><div class="grow"><b>${esc(s.tableName)}</b> ${voided?'<span class="tag warn">Anulada</span>':''} <span class="tag">${methodLabel[s.method]}</span><div class="muted">${fmtDT(s.createdAt)}, ${min}, ${esc(s.createdBy)}</div></div><b>${money(s.total)}</b>${voided?'':`<button class="link" data-a="void" data-id="${s.id}">Anular</button>`}</li>`;
  }).join('') || '<li class="empty">Sin ventas en este periodo.</li>';

  const bit = D.audit.slice(0,40).map(l => `<li><div class="grow">${esc(l.action)}<div class="muted">${fmtDT(l.at)}, ${esc(l.username)}</div></div></li>`).join('') || '<li class="empty">Sin registros.</li>';

  return `<div class="sectionhead"><h2>Informes</h2></div>
    <div class="chips" role="group" aria-label="Periodo">
      ${[['hoy','Hoy'],['7','7 días'],['30','30 días']].map(p => `<button class="chip ${period===p[0]?'on':''}" data-a="period" data-p="${p[0]}">${p[1]}</button>`).join('')}
    </div>
    <div class="kpis" style="margin-top:12px">
      <div class="kpi"><small>Ingresos</small><div class="big">${money(R.income)}</div></div>
      <div class="kpi"><small>Utilidad estimada</small><div class="big ${R.profit<0?'neg':''}">${money(R.profit)}</div></div>
      <div class="kpi"><small>Tiempo de mesa</small><div class="big">${money(st.tableValue)}</div><small>${st.tableSessions} partidas, ${horas} h</small></div>
      <div class="kpi"><small>Consumos</small><div class="big">${money(st.itemsValue)}</div><small>Gastos: ${money(st.expenses)}</small></div>
    </div>
    <div class="card"><h2>Ingresos por día</h2><div class="bars">${bars}</div></div>
    <div class="card"><h2>Alertas de control</h2><ul class="lines">${al.join('') || '<li class="empty">Sin alertas en este periodo. Todo cuadra.</li>'}</ul></div>
    <div class="card"><h2>Fiados pendientes ${R.creditPending.length?`<span class="tag warn">${money(R.creditPendingTotal)}</span>`:''}</h2><ul class="lines">${fiados}</ul></div>
    <div class="card"><h2>Ventas</h2><ul class="lines">${ventas}</ul></div>
    <div class="card"><h2>Bitácora de acciones</h2><ul class="lines">${bit}</ul></div>
    <div class="actions"><button class="btn primary" data-a="copysummary">Copiar resumen para WhatsApp</button></div>`;
}

function viewAjustes(){
  const s = D.settings;
  const tbl = D.tables.map(t => `<div class="row"><span style="min-width:70px">${esc(t.name)}</span><input class="rateIn" data-id="${t.id}" data-orig="${t.ratePerHour}" inputmode="numeric" value="${t.ratePerHour}" aria-label="Tarifa por hora de ${esc(t.name)}"><button class="link" data-a="deltable" data-id="${t.id}">Quitar</button></div>`).join('');
  const users = D.users.map(u => `<li><div class="grow"><b>${esc(u.fullName)}</b> <span class="tag">${u.role==='OWNER'?'Dueño':'Empleado'}</span> ${u.active?'':'<span class="tag warn">Desactivado</span>'}<div class="muted">${esc(u.username)}</div></div>
      <button class="link" data-a="resetpw" data-id="${u.id}">Contraseña</button>
      ${u.username===USER.username?'':`<button class="link" data-a="toggleuser" data-id="${u.id}" data-active="${u.active?'0':'1'}">${u.active?'Desactivar':'Activar'}</button>`}</li>`).join('');
  return `<div class="sectionhead"><h2>Ajustes</h2></div>
    <div class="card"><h2>Negocio</h2>
      <label for="sName">Nombre</label><input id="sName" value="${esc(s.businessName)}">
      <label for="sBase">Base de caja (pesos)</label><input id="sBase" inputmode="numeric" value="${s.baseCash}">
    </div>
    <div class="card"><h2>Cobro del tiempo</h2>
      <label for="sStep">Se cobra por fracciones de</label>
      <select id="sStep">${[1,5,10,15,30].map(n => `<option value="${n}" ${s.billingStepMinutes===n?'selected':''}>${n===1?'Minuto exacto':n+' minutos'}</option>`).join('')}</select>
    </div>
    <div class="card"><h2>Mesas y tarifa por hora</h2>${tbl}
      <div class="actions"><button class="btn" data-a="addtable">Agregar mesa</button></div>
    </div>
    <div class="actions"><button class="btn primary" data-a="savesettings">Guardar ajustes</button></div>
    <div class="sectionhead"><h2>Usuarios</h2><button class="btn small" data-a="newuser">Nuevo usuario</button></div>
    <div class="card"><ul class="lines">${users || '<li class="empty">Cargando…</li>'}</ul></div>`;
}

/* ---------- hojas (modales) ---------- */
const head = t => `<div class="shead"><h3>${esc(t)}</h3><button class="x" data-a="close-sheet">Cerrar</button></div>`;
const sessionOf = id => D.sessions.find(x => x.id === id);

function methodsHtml(m){
  return `<div class="methods" role="radiogroup" aria-label="Medio de pago">
    ${Object.keys(methodLabel).map(k => `<label><input type="radio" name="m" value="${k}" data-change="method" ${m===k?'checked':''}>${methodLabel[k]}</label>`).join('')}
  </div>
  <div id="fiadoBox" ${m==='CREDIT'?'':'hidden'}><label for="cust">Nombre del cliente</label><input id="cust" autocomplete="off" value="${esc(sheet.cust||'')}"></div>
  <div id="cortesiaBox" ${m==='COURTESY'?'':'hidden'}><label for="reason">Motivo de la cortesía</label><input id="reason" autocomplete="off" value="${esc(sheet.reason||'')}"><p class="muted">El dueño verá esta cortesía en sus informes.</p></div>`;
}
function keepFields(){
  if ($('#cust')) sheet.cust = $('#cust').value;
  if ($('#reason')) sheet.reason = $('#reason').value;
}
function payFields(){
  return {method: sheet.method, customerName: ($('#cust') ? $('#cust').value : '').trim(), note: ($('#reason') ? $('#reason').value : '').trim()};
}
function checkPay(f){
  if (f.method === 'CREDIT' && !f.customerName) { toast('Escribe el nombre del cliente'); return false; }
  if (f.method === 'COURTESY' && !f.note) { toast('Escribe el motivo de la cortesía'); return false; }
  return true;
}

const sheets = {
  items(){
    const s = sessionOf(sheet.id);
    const lines = s.items.map(i => `<li><div class="grow">${i.qty} × ${esc(i.name)}</div><span>${money(i.price*i.qty)}</span><button class="link" data-a="rem" data-pid="${i.productId}">Quitar 1</button></li>`).join('') || '<li class="empty">Toca un producto para agregarlo a la cuenta.</li>';
    const prods = D.products.map(p => `<button class="prod" data-a="add" data-pid="${p.id}" ${p.stock<=0?'disabled':''}><b>${esc(p.name)}</b><span>${money(p.price)}</span><small>${p.stock<=0?'Agotado':p.stock+' disponibles'}</small></button>`).join('');
    return head(s.tableName) + `<div class="prodgrid">${prods}</div><h4>En la cuenta</h4><ul class="lines">${lines}</ul>
      <div class="row total"><span>Consumos</span><b>${money(s.itemsTotal)}</b></div>
      <div class="actions"><button class="btn primary" data-a="close-sheet">Listo</button></div>`;
  },
  charge(){
    const s = sessionOf(sheet.id);
    const lines = s.items.map(i => `<div class="row"><span>${i.qty} × ${esc(i.name)}</span><span>${money(i.price*i.qty)}</span></div>`).join('');
    const total = s.timeValueSoFar + s.itemsTotal;
    return head('Cobrar '+s.tableName) +
      `${s.tableId === undefined ? '' : `<div class="row"><span>Tiempo hasta ahora</span><span>${money(s.timeValueSoFar)}</span></div>`}${lines}
       <div class="row total"><span>Total estimado</span><b>${money(total)}</b></div>
       <p class="muted">El servidor calcula el total final en el momento de cobrar.</p>
       ${methodsHtml(sheet.method)}
       <div class="actions"><button class="btn" data-a="close-sheet">Volver</button><button class="btn primary" data-a="confirm-charge">Registrar cobro</button></div>`;
  },
  quick(){
    const entries = Object.entries(sheet.cart);
    let total = 0;
    const lines = entries.map(([pid, q]) => { const p = D.products.find(x => x.id === Number(pid)); total += p.price*q;
      return `<li><div class="grow">${q} × ${esc(p.name)}</div><span>${money(p.price*q)}</span><button class="link" data-a="quick-rem" data-pid="${pid}">Quitar 1</button></li>`; }).join('') || '<li class="empty">Toca un producto para agregarlo.</li>';
    const prods = D.products.map(p => { const left = p.stock - (sheet.cart[p.id] || 0);
      return `<button class="prod" data-a="quick-add" data-pid="${p.id}" ${left<=0?'disabled':''}><b>${esc(p.name)}</b><span>${money(p.price)}</span><small>${left<=0?'Agotado':left+' disponibles'}</small></button>`; }).join('');
    return head('Venta rápida') + `<div class="prodgrid">${prods}</div><h4>En la cuenta</h4><ul class="lines">${lines}</ul>
      <div class="row total"><span>Total</span><b>${money(total)}</b></div>
      ${methodsHtml(sheet.method)}
      <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="quick-confirm" ${entries.length?'':'disabled'}>Cobrar</button></div>`;
  },
  expense(){
    return head('Registrar gasto') +
      `<label for="exC">Concepto</label><input id="exC" autocomplete="off">
       <label for="exA">Valor</label><input id="exA" inputmode="numeric">
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="save-expense">Guardar gasto</button></div>`;
  },
  closeshift(){
    const sh = D.shift;
    return head('Cerrar turno') +
      `<p>Cuenta el efectivo de la caja, incluida la base, y escribe el total.</p>
       ${sh && sh.openTables ? `<p class="neg">Hay ${sh.openTables} mesa${sh.openTables>1?'s':''} abierta${sh.openTables>1?'s':''}. Seguirán corriendo en el siguiente turno.</p>` : ''}
       ${isOwner() && sh && sh.expectedCash !== undefined ? `<p class="muted">Esperado: <b>${money(sh.expectedCash)}</b></p>` : ''}
       <label for="counted">Efectivo contado</label><input id="counted" inputmode="numeric" autocomplete="off">
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="confirm-close">Cerrar turno</button></div>`;
  },
  prod(){
    const p = D.products.find(x => x.id === sheet.id);
    return head(p ? 'Editar producto' : 'Nuevo producto') +
      `<label for="pN">Nombre</label><input id="pN" value="${esc(p?p.name:'')}" autocomplete="off">
       <label for="pP">Precio de venta</label><input id="pP" inputmode="numeric" value="${p?p.price:''}">
       <label for="pC">Costo de compra</label><input id="pC" inputmode="numeric" value="${p?p.cost:''}">
       <label for="pS">${p?'Agregar al stock (compra)':'Stock inicial'}</label><input id="pS" inputmode="numeric" placeholder="0">
       ${p?`<p class="muted">Stock actual: ${p.stock}</p>`:''}
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button>${p?'<button class="btn danger" data-a="del-prod">Eliminar</button>':''}<button class="btn primary" data-a="save-prod">Guardar</button></div>`;
  },
  count(){
    const rows = D.products.map(p => `<div class="row"><label for="c_${p.id}" style="margin:0;flex:1;color:var(--ink)">${esc(p.name)}</label><input id="c_${p.id}" class="cnt" data-pid="${p.id}" inputmode="numeric" style="width:90px" placeholder="—"></div>`).join('');
    return head('Contar inventario') +
      `<p class="muted">Cuenta lo que hay físicamente y escríbelo. Deja en blanco lo que no contaste. Las diferencias quedan registradas.</p>${rows}
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="save-count">Guardar conteo</button></div>`;
  },
  void(){
    const s = D.sales.find(x => x.id === sheet.id);
    return head('Anular venta') +
      `<p>${esc(s.tableName)}, ${money(s.total)}, ${fmtDT(s.createdAt)}.</p><p class="muted">Los productos vuelven al inventario. La anulación queda registrada.</p>
       <div class="actions"><button class="btn" data-a="close-sheet">Volver</button><button class="btn danger" data-a="confirm-void">Anular</button></div>`;
  },
  paid(){
    const s = D.summary.creditPending.find(x => x.id === sheet.id);
    return head('Cobrar fiado') +
      `<p><b>${esc(s.customerName)}</b> paga ${money(s.total)}.</p>
       <div class="methods"><label><input type="radio" name="pm" value="CASH" checked>Efectivo</label><label><input type="radio" name="pm" value="TRANSFER">Transferencia</label></div>
       <div class="actions"><button class="btn" data-a="close-sheet">Volver</button><button class="btn primary" data-a="confirm-paid">Registrar pago</button></div>`;
  },
  newuser(){
    return head('Nuevo usuario') +
      `<label for="uN">Usuario (para entrar)</label><input id="uN" autocomplete="off" autocapitalize="none">
       <label for="uF">Nombre completo</label><input id="uF" autocomplete="off">
       <label for="uP">Contraseña (mínimo 8 caracteres)</label><input id="uP" type="password" autocomplete="new-password">
       <label for="uR">Rol</label><select id="uR"><option value="EMPLOYEE">Empleado</option><option value="OWNER">Dueño</option></select>
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="save-user">Crear usuario</button></div>`;
  },
  resetpw(){
    const u = D.users.find(x => x.id === sheet.id);
    return head('Contraseña de '+u.username) +
      `<label for="nP">Nueva contraseña (mínimo 8 caracteres)</label><input id="nP" type="password" autocomplete="new-password">
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="save-pw">Guardar</button></div>`;
  },
  newtab(){
    return head('Nueva cuenta') +
        `<p class="muted">Escribe a quién o a qué mesa le llevas la cuenta. Ejemplos: Mesa 3, Juan, Barra.</p>
       <label for="tabName">Nombre o mesa</label><input id="tabName" autocomplete="off">
       <div class="actions"><button class="btn" data-a="close-sheet">Cancelar</button><button class="btn primary" data-a="save-tab">Abrir cuenta</button></div>`;
  },
  text(){
    return head('Resumen') + `<p class="muted">No se pudo copiar automáticamente. Selecciona el texto y cópialo.</p><textarea rows="10" readonly>${esc(sheet.text)}</textarea>
      <div class="actions"><button class="btn" data-a="close-sheet">Cerrar</button></div>`;
  }
};

function renderSheet(){
  const o = $('#sheet'), p = $('#panel');
  if (sheet && sheet.type === 'items' && !sessionOf(sheet.id)) sheet = null;
  if (!sheet){ o.hidden = true; p.innerHTML = ''; return; }
  const st = p.scrollTop;
  o.hidden = false;
  p.innerHTML = sheets[sheet.type]();
  p.scrollTop = st;
}
function closeSheet(){ sheet = null; render(); }

/* ---------- reloj (usa la hora del servidor) ---------- */
function tick(){
  const now = Date.now() + offset;
  document.querySelectorAll('[data-timer]').forEach(el => { el.textContent = fmtHMS(now - Date.parse(el.dataset.start)); });
  document.querySelectorAll('[data-est]').forEach(el => { el.textContent = money(timeValue(now - Date.parse(el.dataset.start), Number(el.dataset.rate))); });
}
setInterval(tick, 1000);
// Si hay dos celulares o tablets, las mesas se actualizan solas cada 15 segundos.
setInterval(async () => {
  if (!USER || sheet || busy || (typeof document.hidden !== 'undefined' && document.hidden) || tab !== 'mesas') return;
  try { await loadSessions(); render(); } catch(e){}
}, 15000);

/* ---------- acciones ---------- */
function replaceSession(s){
  const i = D.sessions.findIndex(x => x.id === s.id);
  if (i >= 0) D.sessions[i] = s; else D.sessions.push(s);
  offset = Date.parse(s.serverNow) - Date.now();
}
function logout(){
  TOKEN = ''; USER = null; store('bc_token', null); store('bc_user', null); sheet = null;
  D.shift = D.summary = null; D.sales = []; D.audit = []; D.users = [];
  render();
}

const A = {
  async login(){
    const u = $('#user').value.trim(), p = $('#pass').value;
    if (!u || !p){ $('#loginErr').textContent = 'Escribe usuario y contraseña'; return; }
    try {
      const r = await api('/api/auth/login', 'POST', {username:u, password:p});
      TOKEN = r.token; USER = {username:r.username, fullName:r.fullName, role:r.role};
      store('bc_token', TOKEN); store('bc_user', JSON.stringify(USER));
      try { localStorage.setItem('bc_last_user', u); } catch(e){}
    } catch(e){ $('#loginErr').textContent = e.message; return; }
    tab = isOwner() ? 'informes' : 'mesas';
    await loadSettings().catch(()=>{});
    render();
    await loadTab();
  },
  logout(){ logout(); },
  tab(d){ tab = d.t; render(); window.scrollTo(0,0); loadTab(); },
  period(d){ period = d.p; render(); loadTab(); },
  'close-sheet'(){ closeSheet(); },

  /* mesas y consumos */
  async start(d){ const s = await api('/api/sessions', 'POST', {tableId:Number(d.id)}); replaceSession(s); render(); },
  items(d){ sheet = {type:'items', id:Number(d.id)}; render(); },
  async add(d){
    const s = await api('/api/sessions/'+sheet.id+'/items', 'POST', {productId:Number(d.pid)});
    replaceSession(s);
    const p = D.products.find(x => x.id === Number(d.pid)); if (p) p.stock--;
    render();
  },
  async rem(d){
    const s = await api('/api/sessions/'+sheet.id+'/items/'+d.pid, 'DELETE');
    replaceSession(s);
    const p = D.products.find(x => x.id === Number(d.pid)); if (p) p.stock++;
    render();
  },
  async charge(d){
    await loadSessions();
    if (!sessionOf(Number(d.id))){ render(); return toast('Esa mesa ya fue cobrada'); }
    sheet = {type:'charge', id:Number(d.id), method:'CASH', cust:'', reason:''}; render();
  },
  async 'confirm-charge'(){
    const f = payFields(); if (!checkPay(f)) return;
    const sale = await api('/api/sessions/'+sheet.id+'/charge', 'POST', f);
    D.sessions = D.sessions.filter(x => x.id !== sheet.id);
    sheet = null; render(); toast('Cobro registrado: '+money(sale.total));
    loadProducts().then(render).catch(()=>{});
  },

  /* venta rápida */
  async venta(){
    if (!D.products.length) await loadProducts();
    sheet = {type:'quick', cart:{}, method:'CASH', cust:'', reason:''}; render();
  },
  'quick-add'(d){
    const p = D.products.find(x => x.id === Number(d.pid)), cur = sheet.cart[d.pid] || 0;
    if (!p || cur >= p.stock) return toast('Sin stock');
    keepFields(); sheet.cart[d.pid] = cur + 1; render();
  },
  'quick-rem'(d){
    keepFields(); sheet.cart[d.pid]--; if (sheet.cart[d.pid] <= 0) delete sheet.cart[d.pid]; render();
  },
  async 'quick-confirm'(){
    const f = payFields(); if (!checkPay(f)) return;
    const items = Object.entries(sheet.cart).map(([k, v]) => ({productId:Number(k), qty:v}));
    const sale = await api('/api/sales/quick', 'POST', {items, method:f.method, customerName:f.customerName, note:f.note});
    sheet = null; render(); toast('Venta registrada: '+money(sale.total));
    loadProducts().then(render).catch(()=>{});
  },

  /* caja */
  /* cuentas abiertas */
  newtab(){ sheet = {type:'newtab'}; render(); },
  async 'save-tab'(){
    const label = $('#tabName').value.trim();
    if (!label) return toast('Escribe un nombre para la cuenta');
    if (!D.products.length) await loadProducts();
    const s = await api('/api/tabs', 'POST', {label});
    replaceSession(s);
    sheet = {type:'items', id:s.id}; render();
  },
  expense(){ sheet = {type:'expense'}; render(); },
  async 'save-expense'(){
    const c = $('#exC').value.trim(), v = num($('#exA').value);
    if (!c || !v) return toast('Completa concepto y valor');
    await api('/api/expenses', 'POST', {concept:c, amount:v});
    sheet = null; await loadShift(); render();
  },
  async closeshift(){ await loadShift(); sheet = {type:'closeshift'}; render(); },
  async 'confirm-close'(){
    const raw = $('#counted').value.trim();
    if (raw === '') return toast('Escribe el efectivo contado');
    const r = await api('/api/cash/close', 'POST', {countedCash:num(raw)});
    const msg = r.difference === undefined ? r.message : (r.difference === 0 ? 'Turno cerrado. La caja cuadra.' : 'Turno cerrado. Diferencia: '+money(r.difference));
    logout();
    toast(msg.replace(/\.$/, '') + '. Sesión cerrada.', 6000);  },

  /* productos e inventario */
  prod(d){ if (!isOwner()) return; sheet = {type:'prod', id:d.id === 'new' ? 'new' : Number(d.id)}; render(); },
  async 'save-prod'(){
    const n = $('#pN').value.trim(), pr = num($('#pP').value), co = num($('#pC').value), add = num($('#pS').value);
    if (!n || !pr) return toast('Falta nombre o precio');
    if (sheet.id === 'new') await api('/api/products', 'POST', {name:n, price:pr, cost:co, initialStock:add});
    else {
      await api('/api/products/'+sheet.id, 'PUT', {name:n, price:pr, cost:co, initialStock:0});
      if (add) await api('/api/products/'+sheet.id+'/purchase', 'POST', {qty:add});
    }
    sheet = null; await loadProducts(); render();
  },
  async 'del-prod'(){ await api('/api/products/'+sheet.id, 'DELETE'); sheet = null; await loadProducts(); render(); },
  count(){ sheet = {type:'count'}; render(); },
  async 'save-count'(){
    const lines = [];
    document.querySelectorAll('.cnt').forEach(i => { if (i.value.trim() !== '') lines.push({productId:Number(i.dataset.pid), counted:num(i.value)}); });
    if (!lines.length) return toast('No escribiste ningún conteo');
    const r = await api('/api/inventory/count', 'POST', {lines});
    sheet = null; await loadProducts(); render();
    toast('Conteo guardado'+(r.differences ? ', con '+r.differences+' diferencia'+(r.differences>1?'s':'') : ', sin diferencias'));
  },

  /* informes */
  void(d){ if (!isOwner()) return; sheet = {type:'void', id:Number(d.id)}; render(); },
  async 'confirm-void'(){ await api('/api/sales/'+sheet.id+'/void', 'POST'); sheet = null; await loadReports(); render(); toast('Venta anulada'); },
  paid(d){ sheet = {type:'paid', id:Number(d.id)}; render(); },
  async 'confirm-paid'(){
    const r = document.querySelector('input[name="pm"]:checked');
    await api('/api/sales/'+sheet.id+'/pay', 'POST', {method:r ? r.value : 'CASH'});
    sheet = null; await loadReports(); render(); toast('Pago registrado');
  },
  async copysummary(){
    const R = D.summary, st = R.stats, label = range().label;
    const text = `*${D.settings.businessName}* - ${label}\nIngresos: ${money(R.income)}\nMesas: ${money(st.tableValue)} (${st.tableSessions} partidas, ${(st.minutes/60).toFixed(1)} h)\nConsumos: ${money(st.itemsValue)}\nEfectivo: ${money(st.cash)}\nTransferencias: ${money(st.transfer)}\nFiado nuevo: ${money(st.creditNew)}\nCortesías: ${money(st.courtesy)}\nGastos: ${money(st.expenses)}\nUtilidad estimada: ${money(R.profit)}`;
    try { await navigator.clipboard.writeText(text); toast('Resumen copiado'); }
    catch(e){ sheet = {type:'text', text}; render(); }
  },

  /* ajustes */
  async savesettings(){
    await api('/api/settings', 'PUT', {businessName:$('#sName').value.trim() || 'Mi Billar', billingStepMinutes:Number($('#sStep').value), baseCash:num($('#sBase').value)});
    const inputs = Array.from(document.querySelectorAll('.rateIn'));
    for (const i of inputs){
      const rate = num(i.value);
      if (rate > 0 && String(rate) !== i.dataset.orig){
        const t = D.tables.find(x => x.id === Number(i.dataset.id));
        await api('/api/tables/'+t.id, 'PUT', {name:t.name, ratePerHour:rate});
      }
    }
    await Promise.all([loadSettings(), loadTables()]); render(); toast('Ajustes guardados');
  },
  async addtable(){
    const rate = D.tables.length ? D.tables[0].ratePerHour : 6000;
    await api('/api/tables', 'POST', {name:'Mesa '+(D.tables.length+1), ratePerHour:rate});
    await loadTables(); render();
  },
  async deltable(d){ await api('/api/tables/'+d.id, 'DELETE'); await loadTables(); render(); },
  newuser(){ sheet = {type:'newuser'}; render(); },
  async 'save-user'(){
    const body = {username:$('#uN').value.trim(), fullName:$('#uF').value.trim(), password:$('#uP').value, role:$('#uR').value};
    if (!body.username || !body.fullName) return toast('Completa usuario y nombre');
    if (body.password.length < 8) return toast('La contraseña debe tener mínimo 8 caracteres');
    await api('/api/users', 'POST', body);
    sheet = null; await loadUsers(); render(); toast('Usuario creado');
  },
  async toggleuser(d){ await api('/api/users/'+d.id+'/active?value='+(d.active === '1'), 'PATCH'); await loadUsers(); render(); },
  resetpw(d){ sheet = {type:'resetpw', id:Number(d.id)}; render(); },
  async 'save-pw'(){
    const p = $('#nP').value;
    if (p.length < 8) return toast('La contraseña debe tener mínimo 8 caracteres');
    await api('/api/users/'+sheet.id+'/password', 'PUT', {password:p});
    sheet = null; render(); toast('Contraseña actualizada');
  }
};

document.addEventListener('click', async e => {
  const el = e.target.closest('[data-a]');
  if (el && A[el.dataset.a]){
    if (busy) return;
    busy = true;
    try { await A[el.dataset.a](el.dataset); }
    catch(err){ toast(err.message || 'Ocurrió un error'); }
    finally { busy = false; }
    return;
  }
  if (e.target.id === 'sheet') closeSheet();
});
document.addEventListener('change', e => {
  if (e.target.dataset && e.target.dataset.change === 'method' && sheet){
    sheet.method = e.target.value;
    $('#fiadoBox').hidden = sheet.method !== 'CREDIT';
    $('#cortesiaBox').hidden = sheet.method !== 'COURTESY';
  }
});
document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && (e.target.id === 'pass' || e.target.id === 'user')) A.login();
});

render();
if (USER) { loadSettings().then(render).catch(()=>{}); loadTab(); }
