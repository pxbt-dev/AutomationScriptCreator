/**
 * AutomationScriptCreator - Frontend Logic
 * Communicates with Spring Boot backend on /api/v1/*
 */

// ── State ──────────────────────────────────────────────────────────────────
const state = {
    swagger: { spec: null, tests: [], results: [] },
    website: { elements: [], tests: [], script: '', url: '' },
    agents: { enhanced: [], security: [], a11y: [] },
    stats: { tests: 0, endpoints: 0, passed: 0, ai: 0 }
};

// ── API Helpers ─────────────────────────────────────────────────────────────
async function api(method, path, body) {
    const opts = {
        method,
        headers: { 'Content-Type': 'application/json' }
    };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch('/api/v1' + path, opts);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    return res.json();
}

const get = (path) => api('GET', path);
const post = (path, body) => api('POST', path, body);

// ── Utilities ────────────────────────────────────────────────────────────────
function el(id) { return document.getElementById(id); }
function show(id) { const e = el(id); if (e) e.style.display = ''; }
function hide(id) { const e = el(id); if (e) e.style.display = 'none'; }

function setStatus(id, type, msg) {
    const e = el(id);
    if (!e) return;
    e.className = `status-banner ${type}`;
    e.innerHTML = msg;
}

function clearStatus(id) {
    const e = el(id);
    if (!e) return;
    e.className = 'status-banner';
    e.innerHTML = '';
}

function setBtn(id, loading, label) {
    const b = el(id);
    if (!b) return;
    b.disabled = loading;
    b.innerHTML = loading
        ? `<span class="spinner spinner-sm"></span> ${label || 'Working…'}`
        : (label || b.dataset.label || b.innerHTML);
}

function renderTestCases(containerId, tests) {
    const c = el(containerId);
    if (!c) return;
    if (!tests || tests.length === 0) {
        c.innerHTML = '<div class="empty-state"><div class="empty-icon">📭</div><div class="empty-title">No tests</div></div>';
        return;
    }
    c.innerHTML = tests.map((tc, i) => `
    <div class="test-case-card">
      <div class="test-case-header" onclick="toggleTestCase('tc-${containerId}-${i}')">
        <div class="test-case-priority priority-${(tc.priority || 'medium').toLowerCase()}"></div>
        <div class="test-case-info">
          <div class="test-case-title">${esc(tc.title || 'Untitled')}</div>
          <div class="test-case-meta">
            <span>${tc.id || ''}</span>
            <span>${tc.priority || 'Medium'}</span>
            ${tc.aiEnhanced ? '<span style="color:var(--accent-purple)">🤖 AI Enhanced</span>' : ''}
            ${(tc.tags || []).slice(0, 3).map(t => `<span>${t}</span>`).join('')}
          </div>
        </div>
        <span style="color:var(--text-muted);font-size:11px">▼</span>
      </div>
      <div class="test-case-body" id="tc-${containerId}-${i}">
        ${tc.description ? `<p style="font-size:12px;color:var(--text-secondary);margin-bottom:8px">${esc(tc.description)}</p>` : ''}
        ${tc.precondition ? `<p style="font-size:11px;color:var(--text-muted);margin-bottom:8px">⚠️ Pre: ${esc(tc.precondition)}</p>` : ''}
        ${tc.steps && tc.steps.length ? `
        <div style="font-size:11px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px">Steps</div>
        <ol class="steps-list">${tc.steps.map((s, si) => `<li><span class="step-num">${si + 1}</span><span>${esc(s)}</span></li>`).join('')}</ol>` : ''}
        ${tc.expectedResults && tc.expectedResults.length ? `
        <div style="font-size:11px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin:8px 0 6px">Expected Results</div>
        ${tc.expectedResults.map(r => `<div style="font-size:12px;color:var(--accent-green);padding:2px 0">✓ ${esc(r)}</div>`).join('')}` : ''}
      </div>
    </div>
  `).join('');
}

function toggleTestCase(id) {
    const e = el(id);
    if (e) e.classList.toggle('open');
}

function renderApiResults(containerId, results) {
    const c = el(containerId);
    if (!c) return;
    c.innerHTML = results.map(r => {
        const passed = r.passed;
        const cls = passed ? 'result-pass' : (r.status === 'SKIPPED' ? 'result-skip' : 'result-fail');
        const icon = passed ? '✅' : (r.status === 'SKIPPED' ? '⏭️' : '❌');
        return `
      <div class="result-item ${cls}">
        <span class="result-icon">${icon}</span>
        <span class="result-title">${esc(r.title || r.testId || '')}</span>
        <span class="result-meta">${r.httpStatus ? `HTTP ${r.httpStatus}` : ''} ${r.responseTime || ''}</span>
      </div>
    `;
    }).join('');
}

function esc(s) {
    if (!s) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ── App Navigation ───────────────────────────────────────────────────────────
const App = {
    navigate(page) {
        // Deactivate all
        document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
        document.querySelectorAll('.page-section').forEach(s => s.classList.remove('active'));

        const navEl = el('nav-' + page);
        const pageEl = el('page-' + page);
        if (navEl) navEl.classList.add('active');
        if (pageEl) pageEl.classList.add('active');

        const titles = {
            dashboard: '📊 Dashboard',
            swagger: '📡 API / Swagger Testing',
            website: '🌐 Website Testing',
            agents: '🤖 AI Agents',
            results: '📋 Results & Reports'
        };
        const t = el('page-title');
        if (t) t.innerHTML = titles[page] || page;
    },

    async checkStatus() {
        // Backend
        try {
            const h = await get('/health');
            el('dot-backend').className = 'status-dot online';
            el('lbl-backend').textContent = 'Backend: Online';

            // AI
            const aiOk = h.ai && h.ai.available;
            el('dot-ai').className = `status-dot ${aiOk ? 'online' : 'offline'}`;
            el('lbl-ai').textContent = aiOk ? `AI: ${h.ai.model}` : 'AI: Offline';

            // Playwright
            const pw = h.playwright || {};
            el('dot-playwright').className = `status-dot ${pw.playwrightInstalled ? 'online' : 'offline'}`;
            el('lbl-playwright').textContent = pw.playwrightInstalled ? 'Playwright: Ready' : 'Playwright: Not installed';

            el('dash-ai-status').textContent = aiOk ? '🟢 Online' : '🔴 Offline';
            el('dash-ai-status').className = `card-badge ${aiOk ? 'badge-green' : 'badge-red'}`;

        } catch (_) {
            el('dot-backend').className = 'status-dot offline';
            el('lbl-backend').textContent = 'Backend: Offline';
        }
    },

    updateStats() {
        el('stat-tests').textContent = state.stats.tests;
        el('stat-endpoints').textContent = state.stats.endpoints;
        el('stat-passed').textContent = state.stats.passed;
        el('stat-ai').textContent = state.stats.ai;
    }
};

// ── Swagger / API Page ────────────────────────────────────────────────────────
const SwaggerPage = {
    async parse() {
        const url = el('swagger-url').value.trim();
        if (!url) return setStatus('swagger-status', 'error', '❌ Please enter a Swagger URL');

        setBtn('btn-parse-swagger', true, 'Parsing…');
        clearStatus('swagger-status');
        setStatus('swagger-status', 'info', '⏳ Fetching and parsing spec…');
        hide('swagger-spec-card');
        hide('swagger-gen-card');
        hide('swagger-run-card');

        try {
            const spec = await post('/swagger/parse', { url });
            state.swagger.spec = spec;

            if (!spec.success) {
                return setStatus('swagger-status', 'error', `❌ ${spec.error || 'Parse failed'}`);
            }

            // Show spec info
            el('swagger-api-title').textContent = `${spec.title || 'API'} v${spec.version || '?'}`;
            el('swagger-endpoint-count').textContent = `${spec.endpointCount || 0} endpoints`;
            el('swagger-api-desc').textContent = spec.description || '';

            state.stats.endpoints += spec.endpointCount || 0;
            App.updateStats();

            // Render endpoints
            const endpoints = spec.endpoints || [];
            el('swagger-endpoint-list').innerHTML = endpoints.slice(0, 30).map(ep => `
        <div class="endpoint-item">
          <span class="method-badge method-${(ep.method || '').toLowerCase()}">${ep.method}</span>
          <span class="endpoint-path">${esc(ep.path)}</span>
          <span class="endpoint-summary">${esc(ep.summary || '')}</span>
        </div>
      `).join('') + (endpoints.length > 30 ? `<div style="font-size:12px;color:var(--text-muted);padding:8px">…and ${endpoints.length - 30} more</div>` : '');

            show('swagger-spec-card');
            show('swagger-gen-card');
            setStatus('swagger-status', 'success', `✅ Parsed ${spec.endpointCount} endpoints from ${spec.title}`);

        } catch (e) {
            setStatus('swagger-status', 'error', `❌ ${e.message}`);
        } finally {
            setBtn('btn-parse-swagger', false, 'Parse Spec');
        }
    },

    async generateTests() {
        if (!state.swagger.spec) return;
        const useAI = el('swagger-use-ai').checked;
        setStatus('swagger-gen-status', 'info', `⏳ Generating tests${useAI ? ' with AI' : ''}…`);
        el('swagger-test-list').innerHTML = '';

        try {
            const res = await post('/swagger/generate-tests', { spec: state.swagger.spec, useAI });
            state.swagger.tests = res.testCases || [];

            state.stats.tests += state.swagger.tests.length;
            state.stats.ai += res.aiEnhancedCount || 0;
            App.updateStats();

            renderTestCases('swagger-test-list', state.swagger.tests);
            show('swagger-run-card');
            setStatus('swagger-gen-status', 'success', `✅ Generated ${res.testCaseCount} test cases (${res.aiEnhancedCount || 0} AI-enhanced) in ${((res.processingTimeMs || 0) / 1000).toFixed(1)}s`);
        } catch (e) {
            setStatus('swagger-gen-status', 'error', `❌ ${e.message}`);
        }
    },

    async runTests() {
        if (!state.swagger.tests.length) return;
        const authToken = el('swagger-auth').value.trim();
        setStatus('swagger-run-status', 'info', '⏳ Running API tests…');
        el('swagger-results-list').innerHTML = '';

        try {
            const res = await post('/swagger/run-tests', { testCases: state.swagger.tests, authToken });
            state.swagger.results = res.results || [];

            const s = res.summary || {};
            state.stats.passed += s.passed || 0;
            App.updateStats();

            renderApiResults('swagger-results-list', state.swagger.results);
            ResultsPage.addResults('API', state.swagger.results);

            const cls = s.passRate >= 80 ? 'success' : s.passRate >= 50 ? 'warning' : 'error';
            setStatus('swagger-run-status', cls,
                `${s.passed}/${s.total} passed — ${s.passRate}% pass rate — ${((res.executionTimeMs || 0) / 1000).toFixed(1)}s`);
        } catch (e) {
            setStatus('swagger-run-status', 'error', `❌ ${e.message}`);
        }
    }
};

// ── Website Testing Page ──────────────────────────────────────────────────────
const WebsitePage = {
    async analyse() {
        const url = el('website-url').value.trim();
        if (!url) return setStatus('website-status', 'error', '❌ Please enter a URL');
        state.website.url = url;

        const btn = el('btn-analyse');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner spinner-sm"></span> Analysing…';
        setStatus('website-status', 'info', '⏳ Fetching page and discovering elements…');
        hide('website-analysis-card');
        hide('website-tests-card');
        hide('website-playwright-card');

        try {
            const res = await get('/website/analyze?url=' + encodeURIComponent(url));

            // Stats
            const statsEl = el('website-stats');
            statsEl.innerHTML = [
                ['🔘', 'Buttons', res.buttonCount || 0],
                ['✏️', 'Inputs', res.inputCount || 0],
                ['🔗', 'Links', res.linkCount || 0],
                ['📋', 'Forms', res.formCount || 0],
                ['📦', 'Elements', res.elementCount || (res.elements || []).length]
            ].map(([icon, label, val]) => `
        <div class="stat-card">
          <div class="stat-value" style="font-size:20px">${val}</div>
          <div class="stat-label">${icon} ${label}</div>
        </div>
      `).join('');

            state.website.elements = res.elements || [];

            // Element list (top 15)
            const els = (res.elements || []).slice(0, 15);
            el('website-element-list').innerHTML = els.map(e => `
        <div class="endpoint-item">
          <span class="method-badge method-${typeToColor(e.elementType)}">${e.elementType}</span>
          <span class="endpoint-path">${esc(e.selector)}</span>
          <span class="endpoint-summary">${esc(e.label || '')}</span>
        </div>
      `).join('') || '<div style="color:var(--text-muted);font-size:13px;padding:12px">No interactive elements found</div>';

            show('website-analysis-card');
            show('website-tests-card');
            setStatus('website-status', 'success', `✅ Analysed "${res.title || url}" — ${res.elementCount || 0} elements found`);

        } catch (e) {
            setStatus('website-status', 'error', `❌ ${e.message}`);
        } finally {
            btn.disabled = false;
            btn.innerHTML = 'Analyse';
        }
    },

    async generateTests() {
        const url = state.website.url || el('website-url').value.trim();
        if (!url) return;
        const useAI = el('website-use-ai').checked;

        setStatus('website-gen-status', 'info', `⏳ Generating test cases${useAI ? ' with AI' : ''}…`);
        el('website-test-list').innerHTML = '';

        try {
            const res = await post('/website/generate-tests', { url, useAI });
            state.website.tests = res.testCases || [];

            state.stats.tests += state.website.tests.length;
            state.stats.ai += res.aiEnhancedCount || 0;
            App.updateStats();

            renderTestCases('website-test-list', state.website.tests);
            setStatus('website-gen-status', 'success',
                `✅ Generated ${res.testCaseCount} test cases (${res.aiEnhancedCount || 0} AI) in ${((res.processingTimeMs || 0) / 1000).toFixed(1)}s`);

            // Auto-generate playwright script
            this.generateScript(url, useAI);

        } catch (e) {
            setStatus('website-gen-status', 'error', `❌ ${e.message}`);
        }
    },

    async generateScript(url, useAI) {
        try {
            const res = await post('/website/generate-playwright', { url, useAI });
            if (res.success && res.script) {
                state.website.script = res.script;
                el('playwright-script-code').textContent = res.script;
                const lines = res.script.split('\n').length;
                el('script-filename').textContent = 'generated_test.spec.js';
                el('script-lines').textContent = `${lines} lines`;
                show('website-playwright-card');
            }
        } catch (e) { /* silent */ }
    },

    copyScript() {
        if (!state.website.script) return;
        navigator.clipboard.writeText(state.website.script).then(() => {
            setStatus('website-run-status', 'success', '✅ Script copied to clipboard');
            setTimeout(() => clearStatus('website-run-status'), 2000);
        });
    },

    downloadScript() {
        if (!state.website.script) return;
        const blob = new Blob([state.website.script], { type: 'text/javascript' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'generated_test.spec.js';
        a.click();
    },

    async runScript() {
        if (!state.website.script) return;
        setStatus('website-run-status', 'info', '⏳ Running Playwright tests… (this may take a minute)');

        try {
            const res = await post('/website/run-playwright', {
                script: state.website.script,
                testName: 'website_test_' + Date.now()
            });

            if (res.success) {
                state.stats.passed++;
                App.updateStats();
                show('playwright-report-card');
                setStatus('website-run-status', 'success',
                    `✅ Tests passed in ${((res.executionTime || 0) / 1000).toFixed(1)}s. <a href="/playwright-report/index.html" target="_blank" style="color:var(--accent-blue)">View Report ↗</a>`);
            } else {
                const detail = res.error || (res.output ? `<pre style="margin-top:8px;white-space:pre-wrap;font-size:12px">${res.output}</pre>` : 'See server logs');
                setStatus('website-run-status', 'error', `❌ Tests failed (exit code ${res.exitCode ?? '?'}):<br>${detail}`);
            }

            ResultsPage.addPlaywrightResult(res);

        } catch (e) {
            setStatus('website-run-status', 'error', `❌ ${e.message}`);
        }
    }
};

// ── AI Agents Page ────────────────────────────────────────────────────────────
const AgentPage = {
    async loadStatus() {
        try {
            const s = await get('/agent/status');
            const badge = el('ai-status-badge');
            const msg = el('ai-status-msg');

            if (s.available) {
                badge.textContent = '🟢 Online';
                badge.className = 'card-badge badge-green';
                msg.textContent = s.message;

                const modelsEl = el('ai-models-list');
                modelsEl.innerHTML = (s.availableModels || []).map(m =>
                    `<span class="card-badge badge-blue ai-badge" style="font-size:12px;padding:4px 10px">${m}</span>`
                ).join('');
            } else {
                badge.textContent = '🔴 Offline';
                badge.className = 'card-badge badge-red';
                msg.innerHTML = s.message + `<br><br>
          <strong>To enable AI:</strong><br>
          1. Download Ollama: <a href="https://ollama.com" target="_blank" style="color:var(--accent-blue)">ollama.com</a><br>
          2. Run: <code>ollama pull qwen2.5-coder:7b</code><br>
          3. Start: <code>ollama serve</code>`;
            }
        } catch (e) {
            el('ai-status-msg').textContent = 'Could not connect to AI service';
        }
    },

    switchTab(tab, btn) {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        el('tab-' + tab).classList.add('active');
    },

    async sendChat() {
        const input = el('chat-input');
        const msg = input.value.trim();
        if (!msg) return;
        input.value = '';

        appendChat('user', msg);

        const sendBtn = el('btn-send-chat');
        sendBtn.disabled = true;
        appendChat('ai', '<span class="spinner spinner-sm"></span> Thinking…', 'thinking-msg');

        try {
            const res = await post('/agent/chat', { message: msg });
            const thinkingEl = el('thinking-msg');
            if (thinkingEl) thinkingEl.remove();
            appendChat('ai', res.response || '(No response)');
        } catch (e) {
            const thinkingEl = el('thinking-msg');
            if (thinkingEl) thinkingEl.remove();
            appendChat('ai', `❌ Error: ${e.message}`);
        } finally {
            sendBtn.disabled = false;
        }
    },

    async securityTests() {
        const url = el('sec-url').value.trim();
        const elements = el('sec-elements').value.trim();
        setStatus('sec-status', 'info', '⏳ Security Agent generating tests…');

        try {
            const res = await post('/agent/security-tests', { url, elementSummary: elements });
            state.agents.security = res.testCases || [];
            renderTestCases('sec-test-list', state.agents.security);
            setStatus('sec-status', 'success', `✅ Generated ${res.testCaseCount} security test cases`);
        } catch (e) {
            setStatus('sec-status', 'error', `❌ ${e.message}`);
        }
    },

    async accessibilityTests() {
        const url = el('a11y-url').value.trim();
        const meta = el('a11y-meta').value.trim();
        setStatus('a11y-status', 'info', '⏳ Accessibility Agent generating WCAG tests…');

        try {
            const res = await post('/agent/accessibility-tests', { url, pageMetadata: meta });
            state.agents.a11y = res.testCases || [];
            renderTestCases('a11y-test-list', state.agents.a11y);
            setStatus('a11y-status', 'success', `✅ Generated ${res.testCaseCount} accessibility test cases`);
        } catch (e) {
            setStatus('a11y-status', 'error', `❌ ${e.message}`);
        }
    },

    async enhanceTests() {
        const jsonStr = el('enhance-json').value.trim();
        const context = el('enhance-context').value.trim();
        if (!jsonStr) return setStatus('enhance-status', 'error', '❌ Paste test cases JSON first');

        let testCases;
        try { testCases = JSON.parse(jsonStr); }
        catch (e) { return setStatus('enhance-status', 'error', '❌ Invalid JSON: ' + e.message); }

        setStatus('enhance-status', 'info', '⏳ AI enhancing test cases…');

        try {
            const res = await post('/agent/enhance', { testCases, context });
            state.agents.enhanced = res.testCases || [];
            renderTestCases('enhance-test-list', state.agents.enhanced);
            setStatus('enhance-status', 'success', `✅ Enhanced ${res.testCaseCount} test cases (${res.aiEnhancedCount || 0} with AI)`);
        } catch (e) {
            setStatus('enhance-status', 'error', `❌ ${e.message}`);
        }
    }
};

function appendChat(role, html, id) {
    const messages = el('chat-messages');
    const div = document.createElement('div');
    div.className = `chat-msg ${role}`;
    if (id) div.id = id;
    div.innerHTML = `
    <div class="chat-avatar ${role}">${role === 'ai' ? '🤖' : '👤'}</div>
    <div class="chat-bubble">${html}</div>
  `;
    messages.appendChild(div);
    messages.scrollTop = messages.scrollHeight;
}

// ── Results Page ──────────────────────────────────────────────────────────────
const ResultsPage = {
    all: [],

    addResults(type, results) {
        this.all.push(...results.map(r => ({ ...r, source: type })));
        this.render();
    },

    addPlaywrightResult(res) {
        this.all.push({
            testId: 'PW-' + Date.now(),
            title: 'Playwright Test Suite',
            passed: res.success,
            status: res.success ? 'PASSED' : 'FAILED',
            responseTime: ((res.executionTime || 0) / 1000).toFixed(1) + 's',
            source: 'Playwright'
        });
        this.render();
        if (res.success) show('playwright-report-card');
    },

    render() {
        const c = el('results-list');
        if (!this.all.length) return;

        const passed = this.all.filter(r => r.passed).length;
        const failed = this.all.length - passed;

        el('results-stats').innerHTML = [
            ['Total', this.all.length, 'var(--accent-blue)'],
            ['Passed', passed, 'var(--accent-green)'],
            ['Failed', failed, 'var(--accent-red)']
        ].map(([l, v, c]) => `<div class="stat-card"><div class="stat-value" style="color:${c}">${v}</div><div class="stat-label">${l}</div></div>`).join('');

        c.innerHTML = this.all.map(r => `
      <div class="result-item ${r.passed ? 'result-pass' : 'result-fail'}">
        <span class="result-icon">${r.passed ? '✅' : '❌'}</span>
        <span class="result-title">${esc(r.title || r.testId)}</span>
        <span class="result-meta">${r.source || ''} ${r.httpStatus ? `· HTTP ${r.httpStatus}` : ''} ${r.responseTime ? `· ${r.responseTime}` : ''}</span>
      </div>
    `).join('');
    },

    exportJson() {
        const blob = new Blob([JSON.stringify(this.all, null, 2)], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `test-results-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
    },

    clearResults() {
        this.all = [];
        el('results-list').innerHTML = '<div class="empty-state"><div class="empty-icon">📭</div><div class="empty-title">No results yet</div></div>';
        el('results-stats').innerHTML = '';
        state.stats = { tests: 0, endpoints: 0, passed: 0, ai: 0 };
        App.updateStats();
    }
};

// ── Helpers ───────────────────────────────────────────────────────────────────
function typeToColor(t) {
    return { button: 'post', input: 'put', link: 'get', select: 'patch', form: 'delete', textarea: 'put' }[t] || 'get';
}

// ── Wire Up Navigation ────────────────────────────────────────────────────────
document.querySelectorAll('.nav-item[data-page]').forEach(item => {
    item.addEventListener('click', (e) => {
        e.preventDefault();
        const page = item.getAttribute('data-page');
        App.navigate(page);
        if (page === 'agents') AgentPage.loadStatus();
        if (page === 'results') ResultsPage.render();
    });
});

// ── Init ──────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    App.checkStatus();
    App.updateStats();
    // Refresh status every 30s
    setInterval(App.checkStatus, 30000);
});