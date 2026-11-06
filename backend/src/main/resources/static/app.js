// FlashGuard demo UI — gọi trực tiếp REST API của backend.
const $ = (sel) => document.querySelector(sel);
const money = (v) => Number(v ?? 0).toLocaleString('vi-VN') + ' đ';
const fmtTime = (s) => (s ? new Date(s).toLocaleString('vi-VN') : '');
const newKey = () => (crypto.randomUUID ? crypto.randomUUID() : 'key-' + Date.now() + '-' + Math.random().toString(16).slice(2));

let selectedId = null;

// ---------- API helper ----------
async function api(method, url, body, headers = {}, { log = true } = {}) {
    const opts = { method, headers: { ...headers } };
    if (body !== undefined) {
        opts.headers['Content-Type'] = 'application/json';
        opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    let data = null;
    const text = await res.text();
    if (text) {
        try { data = JSON.parse(text); } catch { data = text; }
    }
    if (log) addLog(method, url, res.status, data);
    return { status: res.status, ok: res.ok, data, headers: res.headers };
}

function addLog(method, url, status, data) {
    const li = document.createElement('li');
    const summary = data && typeof data === 'object'
        ? (data.code ? `${data.code}: ${data.message}` : (data.id ? `id=${data.id}${data.status ? ' ' + data.status : ''}` : ''))
        : '';
    li.className = 's' + String(status)[0];
    li.textContent = `${new Date().toLocaleTimeString('vi-VN')}  ${method} ${url} → ${status} ${summary}`;
    $('#log').prepend(li);
    while ($('#log').children.length > 80) $('#log').lastChild.remove();
}

function toast(msg, isError = false) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast show' + (isError ? ' error' : '');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => (t.className = 'toast'), 3200);
}

function errorText(res) {
    const d = res.data;
    if (d && d.fieldErrors) return Object.values(d.fieldErrors).join('; ');
    if (d && d.message) return d.message;
    return 'Lỗi ' + res.status;
}

function toLocalInput(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

// ---------- Products ----------
async function loadProducts(selectId) {
    const res = await api('GET', '/api/products', undefined, {}, { log: false });
    const sel = $('#productSelect');
    sel.innerHTML = '';
    if (!res.ok || res.data.length === 0) {
        sel.innerHTML = '<option value="">— Chưa có sản phẩm —</option>';
        return;
    }
    for (const p of res.data) {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = `#${p.id} ${p.name} — ${money(p.originalPrice)} — kho ${p.stockQuantity}`;
        sel.append(opt);
    }
    if (selectId) sel.value = selectId;
}

$('#productForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    const res = await api('POST', '/api/products', {
        name: f.get('name'),
        originalPrice: Number(f.get('originalPrice')),
        stockQuantity: Number(f.get('stockQuantity')),
    });
    if (res.ok) {
        toast(`Đã tạo sản phẩm #${res.data.id}`);
        await loadProducts(res.data.id);
    } else toast(errorText(res), true);
});

// ---------- Flash sales ----------
async function loadFlashSales() {
    const res = await api('GET', '/api/flash-sales', undefined, {}, { log: false });
    const list = $('#flashSaleList');
    list.innerHTML = '';
    if (!res.ok || res.data.length === 0) {
        list.innerHTML = '<li class="muted">Chưa có Flash Sale nào</li>';
        return;
    }
    for (const fs of res.data) {
        const li = document.createElement('li');
        li.className = 'item' + (fs.id === selectedId ? ' active' : '');
        li.innerHTML = `<div><div class="name"></div><small></small></div><span class="badge ${fs.phase}">${fs.phase}</span>`;
        li.querySelector('.name').textContent = `#${fs.id} ${fs.productName}`;
        li.querySelector('small').textContent = `${money(fs.salePrice)} · còn ${fs.availableQuantity}/${fs.saleQuantity}`;
        li.addEventListener('click', () => selectFlashSale(fs.id));
        list.append(li);
    }
}

$('#flashSaleForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    const res = await api('POST', '/api/flash-sales', {
        productId: Number(f.get('productId')),
        salePrice: Number(f.get('salePrice')),
        saleQuantity: Number(f.get('saleQuantity')),
        startTime: f.get('startTime'),
        endTime: f.get('endTime'),
        status: f.get('status'),
    });
    if (res.ok) {
        toast(`Đã tạo Flash Sale #${res.data.id}`);
        await loadProducts($('#productSelect').value);
        await selectFlashSale(res.data.id);
    } else toast(errorText(res), true);
});

async function selectFlashSale(id) {
    selectedId = id;
    $('#emptyState').hidden = true;
    $('#detail').hidden = false;
    $('#simResult').hidden = true;
    await refreshDetail();
}

async function refreshDetail() {
    if (!selectedId) return;
    const [fsRes, statRes, ordersRes] = await Promise.all([
        api('GET', `/api/flash-sales/${selectedId}`, undefined, {}, { log: false }),
        api('GET', `/api/flash-sales/${selectedId}/statistics`, undefined, {}, { log: false }),
        api('GET', `/api/flash-sales/${selectedId}/orders`, undefined, {}, { log: false }),
    ]);
    if (!fsRes.ok) return toast(errorText(fsRes), true);
    const fs = fsRes.data;
    const st = statRes.data;

    $('#dTitle').textContent = `Flash Sale #${fs.id} — ${fs.productName}`;
    $('#dMeta').textContent = `Giá gốc ${money(fs.originalPrice)} → giá sale ${money(fs.salePrice)} · ${fmtTime(fs.startTime)} → ${fmtTime(fs.endTime)}`;
    $('#dPhase').textContent = fs.phase;
    $('#dPhase').className = 'badge ' + fs.phase;

    const total = st.saleQuantity || 1;
    $('#barSold').style.width = (st.soldQuantity / total) * 100 + '%';
    $('#barReserved').style.width = (st.reservedQuantity / total) * 100 + '%';
    $('#barAvailable').style.width = (Math.max(st.availableQuantity, 0) / total) * 100 + '%';
    $('#sSold').textContent = st.soldQuantity;
    $('#sReserved').textContent = st.reservedQuantity;
    $('#sAvailable').textContent = st.availableQuantity;
    $('#sTotal').textContent = st.saleQuantity;
    $('#sRevenue').textContent = money(st.revenue);

    const c = $('#dConsistency');
    if (st.oversold || !st.consistent) {
        c.className = 'consistency bad';
        c.textContent = '✗ Số liệu KHÔNG khớp — có dấu hiệu bán vượt hoặc lệch tồn kho!';
    } else {
        c.className = 'consistency good';
        c.textContent = `✓ Không bán vượt · ${st.totalOrders} đơn (PENDING ${st.ordersByStatus.PENDING}, PAID ${st.ordersByStatus.PAID}, CANCELLED ${st.ordersByStatus.CANCELLED}, EXPIRED ${st.ordersByStatus.EXPIRED})`;
    }

    renderOrders(ordersRes.ok ? ordersRes.data : []);
    await loadFlashSales();
}

function renderOrders(orders) {
    const tbody = $('#orderRows');
    tbody.innerHTML = '';
    if (orders.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="muted">Chưa có đơn</td></tr>';
        return;
    }
    for (const o of orders) {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${o.id}</td><td>${o.quantity}</td><td>${money(o.totalAmount)}</td>
            <td><span class="badge ${o.status}">${o.status}</span></td>
            <td>${fmtTime(o.expiresAt)}</td><td class="mono"></td><td class="actions"></td>`;
        tr.querySelector('.mono').textContent = o.idempotencyKey;
        if (o.status === 'PENDING') {
            const actions = tr.querySelector('.actions');
            actions.append(
                actionBtn('Thanh toán', 'ok', () => payOrder(o.id, true)),
                actionBtn('TT lỗi', 'warn', () => payOrder(o.id, false)),
                actionBtn('Hủy', 'no', () => cancelOrder(o.id)),
            );
        }
        tbody.append(tr);
    }
}

function actionBtn(label, cls, onClick) {
    const b = document.createElement('button');
    b.className = 'btn sm ' + cls;
    b.textContent = label;
    b.addEventListener('click', onClick);
    return b;
}

async function payOrder(id, success) {
    const res = await api('POST', `/api/orders/${id}/pay`, { success });
    if (res.ok) toast(success ? `Đơn #${id} đã thanh toán` : `Đơn #${id}: thanh toán thất bại (mô phỏng), đơn vẫn chờ`, !success);
    else toast(errorText(res), true);
    await refreshDetail();
}

async function cancelOrder(id) {
    const res = await api('POST', `/api/orders/${id}/cancel`);
    if (res.ok) toast(`Đã hủy đơn #${id}, trả hàng về kho sale`);
    else toast(errorText(res), true);
    await refreshDetail();
}

// ---------- Đặt hàng ----------
$('#newKeyBtn').addEventListener('click', () => ($('#idemKey').value = newKey()));

$('#orderForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    const body = { quantity: Number(f.get('quantity')) };
    if (f.get('holdSeconds')) body.holdSeconds = Number(f.get('holdSeconds'));
    const res = await api('POST', `/api/flash-sales/${selectedId}/orders`, body, { 'Idempotency-Key': f.get('idempotencyKey') });
    if (res.status === 201) toast(`Đặt hàng thành công: đơn #${res.data.id}`);
    else if (res.status === 200) toast(`Khóa đã dùng → trả lại đơn cũ #${res.data.id} (không tạo đơn trùng)`);
    else toast(errorText(res), true);
    await refreshDetail();
});

// ---------- Mô phỏng đồng thời ----------
$('#simForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    const buyers = Number(f.get('buyers'));
    const qty = Number(f.get('qty'));
    const btn = $('#simBtn');
    btn.disabled = true;
    btn.textContent = `Đang gửi ${buyers} request...`;

    const before = (await api('GET', `/api/flash-sales/${selectedId}/statistics`, undefined, {}, { log: false })).data;
    const started = performance.now();
    const results = await Promise.all(
        Array.from({ length: buyers }, () =>
            api('POST', `/api/flash-sales/${selectedId}/orders`, { quantity: qty }, { 'Idempotency-Key': newKey() }, { log: false })
                .catch(() => ({ status: 0, data: null }))
        )
    );
    const elapsed = Math.round(performance.now() - started);

    const success = results.filter((r) => r.status === 201).length;
    const outOfStock = results.filter((r) => r.data && r.data.code === 'OUT_OF_STOCK').length;
    const other = buyers - success - outOfStock;
    const expectedMax = Math.floor(before.availableQuantity / qty);
    const pass = success <= expectedMax && success === Math.min(buyers, expectedMax);

    addLog('POST×' + buyers, `/api/flash-sales/${selectedId}/orders`, success ? 201 : 409,
        { message: `${success} thành công, ${outOfStock} hết hàng, ${other} lỗi khác` });

    const box = $('#simResult');
    box.hidden = false;
    box.innerHTML = `
        <div class="stat"><b>${success}</b><small>Giữ hàng thành công (201)</small></div>
        <div class="stat"><b>${outOfStock}</b><small>Bị từ chối — hết hàng (409)</small></div>
        <div class="full muted">Lỗi khác: ${other} · Thời gian: ${elapsed} ms · Trước khi bắn còn ${before.availableQuantity} sản phẩm → tối đa ${expectedMax} đơn</div>
        <div class="full ${pass ? 'pass' : 'fail'}">${pass ? '✓ Đúng kỳ vọng: không bán vượt' : '✗ Kết quả lệch kỳ vọng — kiểm tra lại!'}</div>`;

    btn.disabled = false;
    btn.textContent = 'Bắt đầu Flash Sale!';
    await refreshDetail();
});

$('#refreshBtn').addEventListener('click', refreshDetail);

// ---------- Khởi động ----------
(function init() {
    const now = new Date();
    $('#flashSaleForm [name=startTime]').value = toLocalInput(now);
    $('#flashSaleForm [name=endTime]').value = toLocalInput(new Date(now.getTime() + 60 * 60 * 1000));
    $('#idemKey').value = newKey();
    loadProducts();
    loadFlashSales();
    // Tự làm mới để thấy đơn hết hạn được trả hàng
    setInterval(() => { if (selectedId && !$('#simBtn').disabled) refreshDetail(); }, 5000);
})();
