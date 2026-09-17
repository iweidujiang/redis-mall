const titles = {
  catalog: ["商品与缓存", "Cache-Aside：读 Redis，没有再回源；改价先写库再删缓存。"],
  penetration: ["缓存穿透", "查询目录里不存在的 ID，看布隆过滤器和空值缓存。"],
  stampede: ["缓存击穿", "热点 Key 失效后，对比无锁回源和互斥回源打到数据库的次数。"],
  avalanche: ["缓存雪崩", "一批 Key 同时过期，还是把 TTL 打散，看剩余时间是否扎堆。"],
  stock: ["库存超卖", "同样 10 件库存、20 个并发购买，看 GET+SET 和 Lua 的差别。"],
  ratelimit: ["接口限流", "对正式详情接口连发请求，观察分钟级配额和 429。"],
  keys: ["大 Key / 热 Key", "在页面里造出大 Hash 和热点读取，再到 redis-cli 里确认。"]
};

const $ = (id) => document.getElementById(id);

function yuan(cents) {
  return (cents / 100).toFixed(2);
}

async function api(url, options = {}) {
  const res = await fetch(url, options);
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = { message: text }; }
  return { status: res.status, body };
}

function log(el, data) {
  el.textContent = typeof data === "string" ? data : JSON.stringify(data, null, 2);
}

function metric(el, main, sub) {
  el.innerHTML = `${main}<small>${sub}</small>`;
}

function renderProducts(items, dbHits) {
  $("dbHitsPill").textContent = `DB 查询：${dbHits}`;
  $("productCards").innerHTML = items.map((p) => `
    <article class="card">
      <h4>${p.name} <small>#${p.id}</small></h4>
      <p>售价 ¥${yuan(p.priceCents)}</p>
      <p>库存 ${p.stock}</p>
      <p>缓存 TTL ${p.ttlSeconds >= 0 ? p.ttlSeconds + "s" : "未缓存"}</p>
    </article>
  `).join("");
  const opts = items.map((p) => `<option value="${p.id}">${p.id} ${p.name}</option>`).join("");
  $("priceProduct").innerHTML = opts;
}

async function refreshCatalog() {
  const { body } = await api("/api/scenarios/overview");
  if (!body?.ok) {
    log($("catalogLog"), body);
    return;
  }
  const data = body.data;
  $("bloomState").textContent = data.bloomReady ? "布隆过滤器：已启用" : "布隆过滤器：未启用（仅空值缓存）";
  renderProducts(data.items, data.dbHits);
}

document.querySelectorAll("nav button").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll("nav button").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const view = btn.dataset.view;
    document.querySelectorAll(".view").forEach((v) => v.classList.add("hidden"));
    $("view-" + view).classList.remove("hidden");
    $("title").textContent = titles[view][0];
    $("subtitle").textContent = titles[view][1];
    if (view === "catalog") refreshCatalog();
  });
});

$("btnRefreshCatalog").onclick = refreshCatalog;
$("btnResetHits").onclick = async () => {
  await api("/api/scenarios/reset-hits", { method: "POST" });
  refreshCatalog();
};
$("btnUpdatePrice").onclick = async () => {
  const id = $("priceProduct").value;
  const price = $("priceCents").value;
  const { body } = await api(`/api/products/${id}/price?priceCents=${price}`, { method: "PUT" });
  log($("catalogLog"), body);
  refreshCatalog();
};

async function runStampede(naive, el) {
  el.textContent = "执行中…";
  const { body } = await api(`/api/scenarios/stampede?id=1001&threads=20&naive=${naive}`, { method: "POST" });
  if (!body?.ok) {
    metric(el, "失败", body?.message || "请求失败");
    return;
  }
  metric(el, `${body.data.dbHits} 次`, `模式 ${body.data.mode} · ${body.data.threads} 线程`);
}

$("btnStampedeNaive").onclick = () => runStampede(true, $("stampedeNaive"));
$("btnStampedeMutex").onclick = () => runStampede(false, $("stampedeMutex"));

async function runAvalanche(naive, metricEl, logEl) {
  metricEl.textContent = "执行中…";
  const { body } = await api(`/api/scenarios/avalanche?keys=20&naive=${naive}`, { method: "POST" });
  if (!body?.ok) {
    metric(metricEl, "失败", body?.message || "请求失败");
    return;
  }
  const d = body.data;
  metric(metricEl, `${d.distinctTtl} 种 TTL`, `最短 ${d.minTtl}s · 最长 ${d.maxTtl}s`);
  log(logEl, d);
}

$("btnAvalancheNaive").onclick = () => runAvalanche(true, $("avalancheNaive"), $("avalancheNaiveLog"));
$("btnAvalancheJitter").onclick = () => runAvalanche(false, $("avalancheJitter"), $("avalancheJitterLog"));

async function runStock(naive, el) {
  el.textContent = "执行中…";
  const { body } = await api(`/api/scenarios/stock/race?productId=1001&threads=20&naive=${naive}`, { method: "POST" });
  if (!body?.ok) {
    metric(el, "失败", body?.message || "请求失败");
    return;
  }
  const d = body.data;
  metric(el, `卖出 ${d.sold}`, `拒绝 ${d.rejected} · 剩余库存 ${d.stockLeft}`);
}

$("btnStockNaive").onclick = () => runStock(true, $("stockNaive"));
$("btnStockLua").onclick = () => runStock(false, $("stockLua"));
$("btnResetStock").onclick = async () => {
  const id = $("orderProduct").value;
  const { body } = await api(`/api/scenarios/stock/reset?productId=${id}&stock=10`, { method: "POST" });
  log($("orderLog"), body);
};
$("btnOrder").onclick = async () => {
  const { body } = await api("/api/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ productId: Number($("orderProduct").value), qty: Number($("orderQty").value) })
  });
  log($("orderLog"), body);
};

$("btnGhost").onclick = async () => {
  const id = $("ghostId").value;
  const hit = await api(`/api/products/${id}`);
  const meta = await api(`/api/products/${id}/cache-meta`);
  log($("ghostLog"), { query: hit.body, cache: meta.body });
};

$("btnBurst").onclick = async () => {
  $("burstMetric").textContent = "执行中…";
  let ok = 0, limited = 0;
  for (let i = 0; i < 70; i++) {
    const { status } = await api("/api/products/1001");
    if (status === 429) limited++;
    else ok++;
  }
  metric($("burstMetric"), `${limited} 次 429`, `通过 ${ok} · 配额约 60 次/分钟`);
  log($("burstLog"), { ok, limited });
};

$("btnBigKey").onclick = async () => {
  const { body } = await api("/api/scenarios/big-key?fields=20000", { method: "POST" });
  log($("bigLog"), body);
};
$("btnHot").onclick = async () => {
  const { body } = await api("/api/scenarios/hot?times=500", { method: "POST" });
  log($("hotLog"), body);
};

refreshCatalog();
