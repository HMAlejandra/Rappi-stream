/* Modo Ráfaga + Semáforo de Réplicas — Rappi-Stream
   Suscripciones reales por WebSocket: /topic/burst y /topic/workers. */
(function () {
  const COLOR = { ACTIVE:'#34C759', ALERT:'#FFCC00', IDLE:'#555555' };
  let burst = { emitidos:0, procesados:0, throughput:0, elapsed:0, status:null };
  const replicas = new Map();
  let idleTimer = null;

  function montar() {
    const bp = document.getElementById('burstPanelMount');
    if (bp) bp.innerHTML =
      '<div class="burst-panel"><div class="burst-hdr"><span>⚡ Modo Ráfaga</span><div class="burst-btns">' +
      '<button class="rb" onclick="lanzarRafaga(10)">10</button>' +
      '<button class="rb" onclick="lanzarRafaga(25)">25</button>' +
      '<button class="rb" onclick="lanzarRafaga(50)">50</button>' +
      '<button class="rb reset" onclick="reiniciarMetricas()">↻ Reiniciar</button>' +
      '</div></div><div class="burst-metrics">' +
      cell('bEmitted','Emitidos') + cell('bProcessed','Procesados') + cell('bThroughput','órd/s') +
      cell('bElapsed','Transcurrido') + cell('bStatus','Estado') +
      '</div><div class="burst-prog"><div class="burst-prog-fill" id="bProgFill"></div></div></div>';
    const rp = document.getElementById('replicaPanelMount');
    if (rp) rp.innerHTML = '<div class="replica-panel"><div class="replica-hdr">🚦 Semáforo de Réplicas</div>' +
      '<div class="replica-grid" id="replicaGrid"></div></div>';
    renderBurst();
  }
  function cell(id, label) {
    return '<div class="bm"><div class="bm-v" id="'+id+'">0</div><div class="bm-l">'+label+'</div></div>';
  }

  window.lanzarRafaga = function (n) {
    fetch('/api/orders/burst', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ count:n, concurrency:Math.min(n,10), rhythmMillis:0 }) })
      .then(r=>r.json()).then(d=> evLog('⚡ Ráfaga disparada — '+d.emitted+' pedidos','info'))
      .catch(e=> evLog('❌ Error ráfaga: '+e.message,'error'));
  };

  window.reiniciarMetricas = function () {
    fetch('/api/orders/burst/reset',{method:'POST'});
    burst = { emitidos:0, procesados:0, throughput:0, elapsed:0, status:null };
    replicas.clear(); renderBurst(); renderReplicas();
    evLog('↻ Métricas reiniciadas','info');
  };

  function set(id, v) { const el = document.getElementById(id); if (el) el.textContent = v; }
  function renderBurst() {
    set('bEmitted', burst.emitidos);
    set('bProcessed', burst.procesados);
    set('bThroughput', burst.throughput.toFixed(1));
    set('bElapsed', (burst.elapsed/1000).toFixed(1)+'s');
    set('bStatus', burst.status || '—');
    const fill = document.getElementById('bProgFill');
    if (fill) fill.style.width = (burst.emitidos>0 ? Math.min(100, Math.round(burst.procesados*100/burst.emitidos)) : 0) + '%';
  }

  function renderReplicas() {
    const grid = document.getElementById('replicaGrid');
    if (!grid) return;
    const arr = [...replicas.values()].sort((a,b) =>
      a.workerType.localeCompare(b.workerType) || a.replicaId.localeCompare(b.replicaId));
    grid.innerHTML = arr.length ? arr.map(r =>
      '<div class="replica-card"><div class="light" style="background:'+(COLOR[r.status]||COLOR.IDLE)+'"></div>' +
      '<div class="rc-id">'+r.replicaId+'</div><div class="rc-type">'+r.workerType+'</div>' +
      '<div class="rc-count">✓ '+r.processedCount+'</div></div>').join('')
      : '<div class="rc-empty">Sin actividad aún</div>';
  }

  window.burstOnConnect = function (stompCli) {
    stompCli.subscribe('/topic/burst', m => {
      const d = JSON.parse(m.body);
      if (!d || !d.burstId) burst = { emitidos:0, procesados:0, throughput:0, elapsed:0, status:null };
      else burst = { emitidos:d.emitted, procesados:d.processed, throughput:d.throughputPerSec, elapsed:d.elapsedMillis, status:d.status };
      renderBurst();
    });
    stompCli.subscribe('/topic/workers', m => {
      const arr = JSON.parse(m.body);
      (Array.isArray(arr) ? arr : []).forEach(r => replicas.set(r.replicaId, r));
      renderReplicas();
    });
    if (!idleTimer) idleTimer = setInterval(() => {
      const ahora = Date.now(); let cambio = false;
      replicas.forEach(r => {
        if (ahora - new Date(r.lastSeenAt).getTime() > 5000 && r.status !== 'IDLE') { r.status = 'IDLE'; cambio = true; }
      });
      if (cambio) renderReplicas();
    }, 1000);
  };

  document.addEventListener('DOMContentLoaded', montar);
})();
