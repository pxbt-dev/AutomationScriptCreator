class AutomationDebugger {
    constructor() {
        this.baseUrl = 'http://localhost:8080';
        this.activeRequests = new Set();
        this.startTime = null;
        this.abortController = null;
        this.enableDebugging = true;
        this.rawData = null;
        this.aiLimits = null; // Store AI configuration

        // Initialize async
        this.initAsync();
    }

    async initAsync() {
        try {
            this.log('INFO', 'Automation Debugger initialized', { baseUrl: this.baseUrl });
            this.updateStatusIndicators();

            // Load AI limits configuration
            await this.loadAILimits();

            // Test backend connection
            const backendSuccess = await this.testBackendConnection();
            if (backendSuccess) {
                this.log('SUCCESS', 'Backend connection established');
            } else {
                this.log('WARN', 'Backend connection failed - some features may not work');
            }

            // Test AI service
            const aiSuccess = await this.testAI();
            if (aiSuccess) {
                this.log('SUCCESS', 'AI service connection established');
            } else {
                this.log('WARN', 'AI service connection failed - AI features may be limited');
            }

            this.bindEvents();

            // Update UI with loaded limits
            this.updateAILimitsUI();

            this.log('SUCCESS', 'Initialization complete');

        } catch (error) {
            this.log('ERROR', 'Initialization failed', error);
            // Continue anyway - UI might still work
        }
    }

    async loadAILimits() {
        try {
            this.log('INFO', 'Loading AI limits configuration...');

            // Try to fetch AI configuration from backend
            try {
                const response = await fetch(`${this.baseUrl}/api/v1/ai/config`, {
                    timeout: 3000
                }).catch(() => null);

                if (response && response.ok) {
                    const config = await response.json();
                    this.aiLimits = config;
                    this.log('SUCCESS', 'Loaded AI limits from backend', config);
                } else {
                    // Fallback to default values
                    this.loadDefaultAILimits();
                    this.log('INFO', 'Using default AI limits (backend endpoint not available)');
                }
            } catch (error) {
                // Fallback to default values if endpoint doesn't exist
                this.loadDefaultAILimits();
                this.log('INFO', 'Using default AI limits (fetch failed)', error);
            }

        } catch (error) {
            this.log('ERROR', 'Failed to load AI limits', error);
            // Set safe defaults
            this.loadDefaultAILimits();
        }
    }

    loadDefaultAILimits() {
        // Default values that match the backend properties
        this.aiLimits = {
            limitSingle: 1,      // Single AI test mode
            limitAll: 0,         // All AI tests mode (0 = NO LIMIT)
            limitFast: 0,        // Fast mode (no AI)
            limitPlaywright: 5,  // Playwright generation mode
            unlimitedFlag: 0,    // 0 means unlimited
            batchSize: 5,
            timeoutMs: 60000,
            minElementsForAI: 3,
            maxElementsForAI: 100,
            maxTotalTestCases: 100,
            maxAITestsPerRequest: 50,
            maxConcurrentAIRequests: 10
        };

        this.log('INFO', 'Loaded default AI limits', this.aiLimits);
    }

    // loadDefaultAILimits() {
    //     // Default values that match the backend properties
    //     this.aiLimits = {
    //         limitSingle: 1,      // Single AI test mode
    //         limitAll: 0,         // All AI tests mode (0 = NO LIMIT)
    //         limitFast: 0,        // Fast mode (no AI)
    //         limitPlaywright: 5,  // Playwright generation mode
    //         unlimitedFlag: 0,    // 0 means unlimited
    //         batchSize: 10,
    //         timeoutMs: 30000,
    //         minElementsForAI: 3,
    //         maxElementsForAI: 50
    //     };
    //
    //     this.log('INFO', 'Loaded default AI limits', this.aiLimits);
    // }

    updateAILimitsUI() {
        try {
            // Update the HTML elements with current limits
            const elements = {
                'limitFast': this.aiLimits.limitFast,
                'limitSingle': this.aiLimits.limitSingle,
                'limitAll': this.aiLimits.limitAll,
                'limitPlaywright': this.aiLimits.limitPlaywright
            };

            for (const [elementId, value] of Object.entries(elements)) {
                const element = document.getElementById(elementId);
                if (element) {
                    if (elementId === 'limitAll' && value === 0) {
                        element.textContent = '0 (UNLIMITED)';
                        element.classList.add('text-success', 'fw-bold');
                    } else if (elementId === 'limitFast' && value === 0) {
                        element.textContent = '0 (No AI)';
                    } else {
                        element.textContent = value;
                    }
                }
            }

            // Update the AI mode dropdown with current limits
            const aiModeSelect = document.getElementById('aiMode');
            if (aiModeSelect) {
                // Update tooltips or data attributes
                aiModeSelect.setAttribute('data-limit-fast', this.aiLimits.limitFast);
                aiModeSelect.setAttribute('data-limit-single', this.aiLimits.limitSingle);
                aiModeSelect.setAttribute('data-limit-all', this.aiLimits.limitAll);
                aiModeSelect.setAttribute('data-limit-playwright', this.aiLimits.limitPlaywright);
            }

            this.log('INFO', 'Updated UI with AI limits');

        } catch (error) {
            this.log('ERROR', 'Failed to update AI limits UI', error);
        }
    }

    // ========== LOGGING UTILITIES ==========
    log(level, message, data = null) {
        if (!this.enableDebugging && level === 'INFO') return;

        const timestamp = new Date().toLocaleTimeString();
        const logEntry = document.createElement('div');
        logEntry.className = `debug-${level.toLowerCase()}`;

        let logMessage = `[${timestamp}] [${level}] ${message}`;
        if (data) {
            logMessage += `\n${JSON.stringify(data, null, 2)}`;
        }

        logEntry.textContent = logMessage;
        const debugConsole = document.getElementById('debugConsole');
        if (debugConsole) {
            debugConsole.appendChild(logEntry);
            debugConsole.scrollTop = debugConsole.scrollHeight;
        }

        // Browser console logging with fallback
        try {
            if (level === 'ERROR') {
                console.error(`[AutomationDebugger] ${message}`, data || '');
            } else if (level === 'WARN') {
                console.warn(`[AutomationDebugger] ${message}`, data || '');
            } else if (level === 'SUCCESS') {
                console.log(`%c[AutomationDebugger] ${message}`, 'color: green', data || '');
            } else {
                console.log(`[AutomationDebugger] ${message}`, data || '');
            }
        } catch (e) {
            console.log(`[${level}] ${message}`, data || '');
        }
    }

    clearDebugLog() {
        const debugConsole = document.getElementById('debugConsole');
        if (debugConsole) {
            debugConsole.innerHTML = '<div class="debug-info">Debug console cleared.</div>';
        }
    }

    // ========== STATUS MANAGEMENT ==========
    updateStatusIndicators() {
        // This will be populated by connection tests
    }

    updateProgress(percent, text, detail = '') {
        const bar = document.getElementById('progressBar');
        const textEl = document.getElementById('progressText');
        const detailEl = document.getElementById('progressDetail');

        if (bar) bar.style.width = `${percent}%`;
        if (textEl) textEl.textContent = text;
        if (detailEl) detailEl.textContent = detail;

        // Update time elapsed
        if (this.startTime) {
            const elapsed = Math.floor((Date.now() - this.startTime) / 1000);
            const timeElapsedEl = document.getElementById('timeElapsed');
            if (timeElapsedEl) timeElapsedEl.textContent = `${elapsed}s`;
        }
    }

    showProgress(show = true) {
        const section = document.getElementById('progressSection');
        if (section) {
            section.style.display = show ? 'block' : 'none';
            if (show) {
                this.startTime = Date.now();
                this.updateProgress(0, 'Starting operation...', 'Initializing');
            }
        }
    }

    // ========== CONNECTION TESTS ==========
    async testBackendConnection() {
        try {
            this.log('INFO', 'Testing backend connection...');

            const response = await this.fetchWithTimeout(`${this.baseUrl}/api/v1/health`, {
                timeout: 5000
            });

            const data = await response.json();
            this.log('SUCCESS', 'Backend connection successful', data);

            const backendStatus = document.getElementById('backendStatus');
            const backendIndicator = document.getElementById('backendIndicator');

            if (backendStatus) backendStatus.textContent = 'Connected ✓';
            if (backendIndicator) {
                backendIndicator.classList.remove('pulse');
                backendIndicator.className = 'connected';
            }

            return true;
        } catch (error) {
            this.log('ERROR', 'Backend connection failed', error);

            const backendStatus = document.getElementById('backendStatus');
            const backendIndicator = document.getElementById('backendIndicator');

            if (backendStatus) backendStatus.textContent = 'Disconnected ✗';
            if (backendIndicator) {
                backendIndicator.classList.remove('pulse');
                backendIndicator.className = 'disconnected';
            }
            return false;
        }
    }

    async testAI() {
        try {
            this.log('INFO', 'Testing AI service...');
            const start = Date.now();

            const response = await this.fetchWithTimeout(`${this.baseUrl}/api/v1/ai/test`, {
                timeout: 10000
            });

            const data = await response.json();
            const elapsed = Date.now() - start;

            this.log('SUCCESS', `AI service test completed in ${elapsed}ms`, data);

            const aiStatus = document.getElementById('aiStatus');
            const aiIndicator = document.getElementById('aiIndicator');

            if (aiStatus) aiStatus.textContent = `Connected (${elapsed}ms) ✓`;
            if (aiIndicator) {
                aiIndicator.classList.remove('pulse');
                aiIndicator.className = 'connected';
            }

            return true;
        } catch (error) {
            this.log('ERROR', 'AI service test failed', error);

            const aiStatus = document.getElementById('aiStatus');
            const aiIndicator = document.getElementById('aiIndicator');

            if (aiStatus) aiStatus.textContent = 'Disconnected ✗';
            if (aiIndicator) {
                aiIndicator.classList.remove('pulse');
                aiIndicator.className = 'disconnected';
            }
            return false;
        }
    }

    // ========== MAIN OPERATIONS ==========
    async analyzeWebsite() {
        const urlInput = document.getElementById('targetUrl');
        if (!urlInput) return;

        const url = urlInput.value.trim();
        if (!url) {
            this.showToast('Please enter a URL', 'warning');
            return;
        }

        this.log('INFO', 'Starting website analysis', { url });
        this.showProgress(true);
        this.updateProgress(10, 'Validating URL...');

        try {
            // First test if URL is accessible - but don't store the response
            this.updateProgress(20, 'Testing URL accessibility...');
            await fetch(url, { mode: 'no-cors' }).catch(() => {
                // Silently catch - we just want to test if it's reachable
                this.log('INFO', 'URL accessibility test failed or CORS blocked, continuing anyway');
            });

            // Analyze the page
            this.updateProgress(40, 'Sending analysis request to backend...');

            const response = await this.fetchWithTimeout(
                `${this.baseUrl}/api/v1/elements/analyze?url=${encodeURIComponent(url)}`,
                { timeout: 30000 }
            );

            const data = await response.json();
            this.rawData = data;

            this.updateProgress(80, 'Processing results...');
            this.log('SUCCESS', 'Analysis completed', {
                elementCount: data.totalElements,
                htmlLength: data.htmlLength
            });

            // Display results
            this.displayAnalysisResults(data);
            this.updateProgress(100, 'Analysis complete!');

            setTimeout(() => this.showProgress(false), 1000);

        } catch (error) {
            this.log('ERROR', 'Website analysis failed', error);
            this.updateProgress(0, 'Analysis failed');
            this.showToast(`Analysis failed: ${error.message}`, 'danger');
            this.showProgress(false);
        }
    }

    logRawResponse(data) {
        console.log('=== RAW RESPONSE DEBUG ===');
        console.log('Type:', typeof data);
        console.log('Is Array?', Array.isArray(data));
        console.log('Keys:', data ? Object.keys(data) : 'null');

        if (data && data.testCases) {
            console.log('testCases type:', typeof data.testCases);
            console.log('testCases is Array?', Array.isArray(data.testCases));
            if (Array.isArray(data.testCases)) {
                console.log('testCases length:', data.testCases.length);
                if (data.testCases.length > 0) {
                    console.log('First test case:', data.testCases[0]);
                    console.log('First test case keys:', Object.keys(data.testCases[0]));
                }
            }
        }
        console.log('Full response:', JSON.stringify(data, null, 2));
        console.log('=== END DEBUG ===');
    }

    getAILimitForMode(mode) {
        if (!this.aiLimits) {
            this.loadDefaultAILimits();
            this.log('WARN', 'aiLimits was null, loaded defaults');
        }

        if (!mode) {
            this.log('WARN', 'getAILimitForMode called without mode, using "fast"');
            return this.aiLimits?.limitFast || 0;
        }

        const modeLower = mode.toLowerCase();

        switch (modeLower) {
            case 'single':
                return this.aiLimits?.limitSingle || 1;
            case 'all':
                return this.aiLimits?.limitAll || 0;
            case 'fast':
                return this.aiLimits?.limitFast || 0;
            case 'playwright':
                return this.aiLimits?.limitPlaywright || 5;
            default:
                this.log('WARN', `Unknown AI mode: ${mode}, using "fast"`);
                return this.aiLimits?.limitFast || 0;
        }
    }

    async generateTests() {
        const urlInput = document.getElementById('targetUrl');
        const aiModeSelect = document.getElementById('aiMode');

        if (!urlInput || !aiModeSelect) return;

        const url = urlInput.value.trim();
        const aiMode = aiModeSelect.value;

        if (!url) {
            this.showToast('Please enter a URL', 'warning');
            return;
        }

        this.log('INFO', 'Generating test cases', { url, aiMode });
        this.showProgress(true);

        // Create abort controller for this operation
        this.abortController = new AbortController();
        let progressInterval = null;
        let sessionId = null;

        try {
            this.updateProgress(10, 'Preparing test generation...');

            // Get AI limit for selected mode from configuration
            const aiLimit = this.getAILimitForMode(aiMode);

            // Generate a session ID for progress tracking
            sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);

            const requestBody = {
                url: url,
                mode: aiMode,
                useML: true,
                useAI: aiMode !== 'fast',
                enhanceWithAI: aiMode === 'all' || aiMode === 'single',
                sessionId: sessionId // Send session ID for progress tracking
            };

            this.log('INFO', 'Sending request to backend', {
                ...requestBody,
                aiLimit: aiLimit,
                unlimited: aiLimit === 0,
                sessionId: sessionId
            });
            this.updateProgress(30, 'Discovering interactive elements...');

            // Start progress polling
            if (sessionId) {
                progressInterval = setInterval(() => {
                    this.pollGenerationProgress(sessionId);
                }, 2000); // Poll every 2 seconds
            }

            const response = await fetch(`${this.baseUrl}/api/v1/elements/generate-test-cases`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody),
                signal: this.abortController.signal
                // NO timeout - let it run as long as needed
            });

            this.updateProgress(70, 'Generating test cases...');

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP ${response.status}: ${errorText}`);
            }

            const data = await response.json();
            this.rawData = data;
            this.logRawResponse(data); // <-- Add this for debugging

            this.log('SUCCESS', 'Test generation completed', {
                success: data.success,
                testCaseCount: data.testCaseCount,
                testCasesActual: data.testCases ? data.testCases.length : 0,
                enhancedWithAI: data.enhancedWithAI,
                aiLimit: data.aiLimit,
                aiUnlimited: data.aiUnlimited
            });

            this.updateProgress(90, 'Rendering test cases...');

            // Display test cases
            if (data.testCases && data.testCases.length > 0) {
                this.displayTestCases(data.testCases);
                this.log('INFO', `Displayed ${data.testCases.length} test cases`);

                // Show AI limit info in toast
                if (data.aiUnlimited) {
                    this.showToast(`Generated ${data.testCaseCount} test cases with UNLIMITED AI enhancement!`, 'success');
                } else if (data.aiLimit > 0) {
                    const aiEnhancedCount = (data.testCases || []).filter(tc =>
                        tc.tags && tc.tags.includes('ai-enhanced')
                    ).length;
                    this.showToast(`Generated ${data.testCaseCount} test cases (AI-enhanced: ${aiEnhancedCount}/${data.aiLimit})`, 'success');
                } else {
                    this.showToast(`Generated ${data.testCaseCount} test cases!`, 'success');
                }
            } else {
                this.log('WARN', 'No test cases in response', data);
                const container = document.getElementById('testCasesContainer');
                if (container) {
                    container.innerHTML = `
                    <div class="alert alert-warning">
                        <i class="fas fa-exclamation-triangle"></i>
                        No test cases were generated. The response was:
                        <pre class="mt-2">${JSON.stringify(data, null, 2)}</pre>
                    </div>
                `;
                }
                this.showToast('No test cases generated', 'warning');
            }

            this.updateProgress(100, 'Test generation complete!');

            setTimeout(() => {
                this.showProgress(false);
            }, 1000);

        } catch (error) {
            this.log('ERROR', 'Test generation failed', {
                name: error.name,
                message: error.message,
                stack: error.stack
            });

            if (error.name === 'AbortError') {
                this.showToast('Test generation cancelled', 'warning');
            } else {
                this.showToast(`Test generation failed: ${error.message}`, 'danger');
            }
            this.showProgress(false);
        } finally {
            // Clean up
            if (progressInterval) {
                clearInterval(progressInterval);
            }
            this.abortController = null;
        }
    }

    async pollGenerationProgress(sessionId) {
        try {
            const response = await fetch(`${this.baseUrl}/api/v1/elements/generation-progress/${sessionId}`);
            if (response.ok) {
                const progress = await response.json();

                // Use the correct field names from backend
                const percentComplete = progress["percentComplete"];
                const status = progress["status"] || progress["statusMessages"] || 'Processing...';
                const eta = progress["eta"] || progress["estimatedRemainingMs"];

                if (percentComplete !== undefined) {
                    let etaText = 'Calculating...';
                    if (eta) {
                        if (typeof eta === 'number') {
                            // eta is milliseconds
                            const etaDate = new Date(Date.now() + eta);
                            etaText = etaDate.toLocaleTimeString();
                        } else {
                            // eta is a date string
                            etaText = new Date(eta).toLocaleTimeString();
                        }
                    }

                    this.updateProgress(
                        percentComplete,
                        typeof status === 'string' ? status : 'Processing...',
                        `ETA: ${etaText} | ${progress["completedAITests"] || 0}/${progress["aiEnhancedCount"] || 0} AI tests`
                    );
                }
            }
        } catch (error) {
            // Silently fail - progress polling is optional
        }
    }

    async generatePlaywright() {
        const url = document.getElementById('targetUrl').value.trim();
        const includeAI = document.getElementById('includeAIPlaywright').checked;

        if (!url) {
            this.showToast('Please enter a URL', 'warning');
            return;
        }

        this.log('INFO', 'Generating Playwright script', { url, includeAI });
        this.showProgress(true);
        this.updateProgress(30, 'Generating Playwright test script... (this may take several minutes)');

        try {
            // Get AI limit for playwright mode
            const aiLimit = this.getAILimitForMode('playwright');

            // Remove timeout for playwright generation - it can take a long time with AI
            const response = await fetch(`${this.baseUrl}/api/v1/elements/generate-playwright`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    url: url,
                    includeAI: includeAI,
                    aiLimit: includeAI ? aiLimit : 0
                })
                // NO timeout - playwright generation can take a long time, especially with AI
            });

            const data = await response.json();

            if (!data.success) {
                throw new Error(data.error || 'Failed to generate Playwright script');
            }

            this.updateProgress(90, 'Rendering Playwright script...');

            // Display the script
            const scriptContainer = document.getElementById('playwrightScript');
            if (scriptContainer) {
                scriptContainer.textContent = data.playwrightScript || '// No script generated';

                // Add syntax highlighting (optional)
                this.highlightPlaywrightCode(scriptContainer);
            }

            // Update count
            const countElement = document.getElementById('playwrightCount');
            if (countElement) {
                countElement.textContent = data.testCaseCount || 0;
            }

            this.updateProgress(100, 'Playwright script generated!');

            setTimeout(() => {
                this.showProgress(false);
                const message = includeAI && data.aiUnlimited
                    ? `Generated Playwright script with ${data.testCaseCount} test cases (UNLIMITED AI)`
                    : `Generated Playwright script with ${data.testCaseCount} test cases (AI limit: ${data.aiLimit})`;
                this.showToast(message, 'success');

                // Switch to Playwright tab
                const playwrightTab = new bootstrap.Tab(document.querySelector('[data-bs-target="#playwright"]'));
                playwrightTab.show();
            }, 1000);

        } catch (error) {
            this.log('ERROR', 'Playwright generation failed', error);
            if (error.name === 'AbortError') {
                this.showToast('Playwright generation timed out. Try with "Fast (No AI)" mode or reduce AI limits.', 'warning');
            } else {
                this.showToast(`Playwright generation failed: ${error.message}`, 'danger');
            }
            this.showProgress(false);
        }
    }

    copyPlaywright() {
        const scriptContainer = document.getElementById('playwrightScript');
        if (!scriptContainer || !scriptContainer.textContent || scriptContainer.textContent.trim() === '') {
            this.showToast('No Playwright script to copy', 'warning');
            return;
        }

        navigator.clipboard.writeText(scriptContainer.textContent)
            .then(() => this.showToast('Playwright script copied to clipboard', 'success'))
            .catch(err => {
                this.log('ERROR', 'Failed to copy Playwright script', err);
                this.showToast('Failed to copy script', 'danger');
            });
    }

    downloadPlaywright() {
        const scriptContainer = document.getElementById('playwrightScript');
        if (!scriptContainer || !scriptContainer.textContent || scriptContainer.textContent.trim() === '') {
            this.showToast('No Playwright script to download', 'warning');
            return;
        }

        const content = scriptContainer.textContent;
        const blob = new Blob([content], { type: 'application/javascript' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `playwright-test-${new Date().toISOString().slice(0, 10)}.spec.js`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        this.showToast('Playwright script downloaded', 'success');
    }

    highlightPlaywrightCode(element) {
        const code = element.textContent;

        // First, escape HTML entities to prevent double-encoding
        const escapedCode = code
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');

        // Now apply syntax highlighting
        const highlighted = escapedCode
            .replace(/(const|let|var|async|await|function|test|expect|require|import|from|export|default|module\.exports)/g, '<span style="color: #d73a49;">$1</span>')
            .replace(/(\/\/.*)/g, '<span style="color: #6a737d;">$1</span>')
            .replace(/(".*?"|'.*?')/g, '<span style="color: #032f62;">$1</span>')
            .replace(/(\d+)/g, '<span style="color: #005cc5;">$1</span>')
            .replace(/(true|false|null|undefined)/g, '<span style="color: #6f42c1;">$1</span>')
            .replace(/(describe|it|beforeEach|afterEach|beforeAll|afterAll)/g, '<span style="color: #e36209;">$1</span>');

        element.innerHTML = highlighted;
    }

    async runPlaywrightTests() {
        const scriptContainer = document.getElementById('playwrightScript');
        if (!scriptContainer || !scriptContainer.textContent || scriptContainer.textContent.trim() === '') {
            this.showToast('No Playwright script to run', 'warning');
            return;
        }

        const scriptContent = scriptContainer.textContent;
        const testName = 'generated_test_' + Date.now();

        this.log('INFO', 'Running Playwright test', { testName, scriptLength: scriptContent.length });
        this.showProgress(true);
        this.updateProgress(20, 'Sending test to backend for execution...');

        try {
            const response = await this.fetchWithTimeout(
                `${this.baseUrl}/api/v1/playwright/run`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        script: scriptContent,
                        testName: testName
                    }),
                    timeout: 300000 // 5 minutes for test execution
                }
            );

            this.updateProgress(70, 'Running Playwright test...');

            const data = await response.json();
            this.log('SUCCESS', 'Playwright test execution completed', {
                success: data.success,
                exitCode: data.exitCode,
                executionTime: data.executionTime
            });

            this.updateProgress(90, 'Processing results...');

            // Display execution results
            this.displayPlaywrightExecutionResults(data);

            this.updateProgress(100, 'Test execution complete!');

            setTimeout(() => {
                this.showProgress(false);
                if (data.success) {
                    this.showToast(`Playwright test executed successfully in ${data.executionTime}ms`, 'success');
                } else {
                    this.showToast(`Playwright test failed with exit code ${data.exitCode}`, 'danger');
                }
            }, 1000);

        } catch (error) {
            this.log('ERROR', 'Playwright test execution failed', error);
            this.showToast(`Failed to run Playwright test: ${error.message}`, 'danger');
            this.showProgress(false);
        }
    }

    displayPlaywrightExecutionResults(data) {
        const resultsContainer = document.getElementById('playwrightResults');
        if (!resultsContainer) return;

        let html = `
        <div class="card mb-3 ${data.success ? 'border-success' : 'border-danger'}">
            <div class="card-header ${data.success ? 'bg-success text-white' : 'bg-danger text-white'}">
                <i class="fas fa-${data.success ? 'check-circle' : 'times-circle'} me-2"></i>
                Test Execution Results
            </div>
            <div class="card-body">
                <div class="row">
                    <div class="col-md-4">
                        <strong>Status:</strong> ${data.success ? '✅ PASSED' : '❌ FAILED'}
                    </div>
                    <div class="col-md-4">
                        <strong>Exit Code:</strong> ${data.exitCode || 'N/A'}
                    </div>
                    <div class="col-md-4">
                        <strong>Duration:</strong> ${data.executionTime || 0}ms
                    </div>
                </div>
    `;

        if (data.output) {
            html += `
            <div class="mt-3">
                <strong>Output:</strong>
                <pre class="execution-output">${this.escapeHtml(data.output)}</pre>
            </div>
        `;
        }

        if (data.errorOutput) {
            html += `
            <div class="mt-3">
                <strong>Errors:</strong>
                <pre class="error-output">${this.escapeHtml(data.errorOutput)}</pre>
            </div>
        `;
        }

        if (data.htmlReportPath) {
            html += `
            <div class="mt-3">
                <strong>Report:</strong> 
                <a href="${data.htmlReportUrl || '/playwright-report/index.html'}" target="_blank" class="btn btn-sm btn-info">
                    <i class="fas fa-chart-bar me-1"></i> View HTML Report
                </a>
            </div>
        `;
        }

        if (data.testResults) {
            html += `
            <div class="mt-3">
                <strong>Test Results:</strong>
                <pre class="test-results">${JSON.stringify(data.testResults, null, 2)}</pre>
            </div>
        `;
        }

        html += `
            </div>
        </div>
    `;

        resultsContainer.innerHTML = html;
    }

    // ========== DISPLAY FUNCTIONS ==========
    displayAnalysisResults(data) {
        const container = document.getElementById('elementsContainer');
        const countEl = document.getElementById('elementsCount');

        if (!container || !countEl) return;

        if (!data || !data.elementTypes) {
            container.innerHTML = '<p class="text-muted text-center">No analysis data available</p>';
            return;
        }

        countEl.textContent = data.totalElements || 0;

        let html = `
            <div class="row mb-3">
                <div class="col-md-6">
                    <div class="card">
                        <div class="card-body">
                            <h6>Summary</h6>
                            <p>Total Elements: <strong>${data.totalElements}</strong></p>
                            <p>HTML Size: <strong>${this.formatBytes(data.htmlLength)}</strong></p>
                        </div>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="card">
                        <div class="card-body">
                            <h6>Priority Distribution</h6>
                            ${this.renderPriorityDistribution(data.priorityDistribution)}
                        </div>
                    </div>
                </div>
            </div>
            
            <h6>Element Types</h6>
            <div class="table-responsive">
                <table class="table table-sm">
                    <thead>
                        <tr>
                            <th>Type</th>
                            <th>Count</th>
                            <th>Percentage</th>
                        </tr>
                    </thead>
                    <tbody>
        `;

        const total = data.totalElements;
        Object.entries(data.elementTypes || {}).forEach(([type, count]) => {
            const percentage = total > 0 ? ((count / total) * 100).toFixed(1) : 0;
            html += `
                <tr>
                    <td>${type}</td>
                    <td>${count}</td>
                    <td>
                        <div class="progress" style="height: 10px;">
                            <div class="progress-bar" style="width: ${percentage}%"></div>
                        </div>
                        ${percentage}%
                    </td>
                </tr>
            `;
        });

        html += `
                    </tbody>
                </table>
            </div>
        `;

        container.innerHTML = html;
    }

    updateAICounts(testCases) {
        const aiEnhancedCount = testCases ?
            testCases.filter(tc => tc.tags && tc.tags.includes('ai-enhanced')).length : 0;

        const aiCountElement = document.getElementById('aiEnhancedCount');
        if (aiCountElement) {
            aiCountElement.textContent = aiEnhancedCount;
        }

        return aiEnhancedCount;
    }

    displayTestCases(testCases) {
        const container = document.getElementById('testCasesContainer');
        const countEl = document.getElementById('testCasesCount');

        if (!container || !countEl) {
            this.log('ERROR', 'Test case container elements not found');
            return;
        }

        // Better validation
        if (!testCases) {
            this.log('ERROR', 'testCases is null or undefined');
            container.innerHTML = '<p class="text-danger">Error: No test cases data received</p>';
            countEl.textContent = '0';
            return;
        }

        if (!Array.isArray(testCases)) {
            this.log('ERROR', 'testCases is not an array', {
                type: typeof testCases,
                value: testCases,
                constructor: testCases?.constructor?.name
            });
            container.innerHTML = `
            <div class="alert alert-danger">
                <i class="fas fa-exclamation-triangle"></i>
                Error: testCases is not an array. Received: ${typeof testCases}
                ${testCases ? `<pre class="mt-2 small">${JSON.stringify(testCases, null, 2)}</pre>` : ''}
            </div>
        `;
            countEl.textContent = '0';
            return;
        }


        // DECLARE validTestCases FIRST before using it
        let validTestCases = [];

        try {
            validTestCases = testCases.filter(tc =>
                tc && typeof tc === 'object' && tc.title !== undefined
            );
        } catch (error) {
            this.log('ERROR', 'Error filtering test cases', error);
            container.innerHTML = `
            <div class="alert alert-danger">
                <i class="fas fa-exclamation-triangle"></i>
                Error processing test cases: ${error.message}
            </div>
        `;
            countEl.textContent = '0';
            return;
        }

        countEl.textContent = validTestCases.length;

        if (validTestCases.length === 0) {
            container.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-info-circle"></i>
                No valid test cases generated.
                ${testCases.length > 0 ?
                `<br><small>Received ${testCases.length} items but none had valid structure.</small>` :
                '<br><small>Empty test cases array received.</small>'
            }
            </div>
        `;
            return;
        }

        // Now use validTestCases in the rest of the function
        const aiEnhancedCount = this.updateAICounts(validTestCases);

        let html = '';
        validTestCases.forEach((testCase, index) => {
            try {
                const priority = testCase.priority || 'Medium';
                const priorityClass = priority.toLowerCase();
                const hasAI = (testCase.tags && Array.isArray(testCase.tags) && testCase.tags.includes('ai-enhanced')) ||
                    (testCase.title && testCase.title.includes('AI-Enhanced'));

                const steps = Array.isArray(testCase.steps) ? testCase.steps : [];
                const expectedResults = Array.isArray(testCase.expectedResults) ? testCase.expectedResults : [];
                const tags = Array.isArray(testCase.tags) ? testCase.tags : [];

                html += `
                <div class="test-case-card ${priorityClass} mb-3">
                    <div class="d-flex justify-content-between align-items-start">
                        <h6 class="mb-1">${index + 1}. ${this.escapeHtml(testCase.title || 'Untitled Test Case')}</h6>
                        <span class="badge bg-${priorityClass === 'high' ? 'danger' :
                    priorityClass === 'medium' ? 'warning' : 'success'}">
                            ${priority}
                        </span>
                    </div>
                    
                    ${hasAI ? '<span class="badge bg-info mb-2">AI-Enhanced</span>' : ''}
                    
                    ${testCase.description ? `
                        <p class="text-muted small mb-2">${this.escapeHtml(testCase.description)}</p>
                    ` : ''}
                    
                    ${testCase.precondition ? `
                        <div class="mb-2">
                            <strong class="small">Precondition:</strong>
                            <p class="small mb-0">${this.escapeHtml(testCase.precondition)}</p>
                        </div>
                    ` : ''}
                    
                    <div class="row mt-2">
                        <div class="col-md-6">
                            <strong class="small">Steps:</strong>
                            <ol class="small mb-0">
                                ${steps.map(step => `<li>${this.escapeHtml(step)}</li>`).join('')}
                            </ol>
                        </div>
                        <div class="col-md-6">
                            <strong class="small">Expected Results:</strong>
                            <ul class="small mb-0">
                                ${expectedResults.map(result => `<li>${this.escapeHtml(result)}</li>`).join('')}
                            </ul>
                        </div>
                    </div>
                    
                    ${tags.length > 0 ? `
                        <div class="mt-3">
                            <strong class="small">Tags:</strong>
                            ${tags.map(tag => `<span class="badge bg-light text-dark border me-1 small">${this.escapeHtml(tag)}</span>`).join('')}
                        </div>
                    ` : ''}
                    
                    <div class="mt-3 text-end">
                        <button class="btn btn-sm btn-outline-primary" onclick="app.copyTestCase(${index})">
                            <i class="fas fa-copy me-1"></i> Copy
                        </button>
                        <button class="btn btn-sm btn-outline-success ms-1" onclick="app.viewTestCaseDetails(${index})">
                            <i class="fas fa-eye me-1"></i> View Details
                        </button>
                    </div>
                </div>
            `;
            } catch (error) {
                this.log('ERROR', `Error rendering test case ${index}`, { error, testCase });
                html += `
                <div class="alert alert-danger">
                    Error rendering test case ${index + 1}: ${error.message}
                    <pre class="small mt-2">${JSON.stringify(testCase, null, 2)}</pre>
                </div>
            `;
            }
        });

        container.innerHTML = html;
        this.log('INFO', `Displayed ${validTestCases.length} test cases (${aiEnhancedCount} AI-enhanced)`);
    }

    isAIUnlimited(mode) {
        const limit = this.getAILimitForMode(mode);
        return limit === 0; // 0 means unlimited
    }

    async estimateTestCount() {
        const urlInput = document.getElementById('targetUrl');
        const aiModeSelect = document.getElementById('aiMode');

        if (!urlInput || !aiModeSelect) return 0;

        const url = urlInput.value.trim();
        const aiMode = aiModeSelect.value;

        if (!url) return 0;

        try {
            this.log('INFO', 'Estimating test count', { url, aiMode });

            const response = await this.fetchWithTimeout(
                `${this.baseUrl}/api/v1/elements/estimate-test-count`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        url: url,
                        mode: aiMode
                    }),
                    timeout: 10000
                }
            );

            if (response.ok) {
                const data = await response.json();
                this.log('INFO', 'Test count estimation received', data);
                return data.estimatedTests || 0;
            }
        } catch (error) {
            this.log('WARN', 'Test count estimation failed, using default', error);
        }

        // Default estimates based on mode
        switch (aiMode) {
            case 'single': return 15;
            case 'all': return 50;
            case 'playwright': return 20;
            default: return 10; // fast
        }
    }

    updateProgressWithPhase(percent, phase, step, detail = '') {
        const bar = document.getElementById('progressBar');
        const textEl = document.getElementById('progressText');
        const detailEl = document.getElementById('progressDetail');
        const phaseEl = document.getElementById('progressPhase');
        const stepEl = document.getElementById('progressStep');
        const percentEl = document.getElementById('progressPercent');
        const estimatedEl = document.getElementById('estimatedTests');

        const phases = {
            'discovery': '🔍 Discovering Elements',
            'generation': '⚡ Generating Tests',
            'ai_enhancement': '🤖 AI Enhancement',
            'rendering': '🎨 Rendering Results',
            'complete': '✅ Complete'
        };

        const phaseName = phases[phase] || phase;

        if (bar) {
            bar.style.width = `${percent}%`;
            bar.className = `progress-bar progress-bar-striped ${percent < 100 ? 'progress-bar-animated' : ''}`;
        }
        if (textEl) textEl.textContent = `${phaseName}: ${step}`;
        if (detailEl) detailEl.textContent = detail;
        if (phaseEl) phaseEl.textContent = phaseName;
        if (stepEl) stepEl.textContent = step;
        if (percentEl) percentEl.textContent = `${percent}%`;
        if (estimatedEl && this.totalEstimatedSteps > 0) {
            estimatedEl.textContent = this.totalEstimatedSteps;
        }

        // Update time elapsed and estimate
        if (this.startTime) {
            const elapsed = Math.floor((Date.now() - this.startTime) / 1000);
            const timeElapsedEl = document.getElementById('timeElapsed');
            if (timeElapsedEl) {
                const minutes = Math.floor(elapsed / 60);
                const seconds = elapsed % 60;
                timeElapsedEl.textContent = minutes > 0 ?
                    `${minutes}m ${seconds}s` : `${seconds}s`;
            }

            // Estimate remaining time if we have total steps
            if (this.totalEstimatedSteps > 0 && this.completedSteps > 0) {
                const stepsPerSecond = this.completedSteps / elapsed;
                const remainingSteps = this.totalEstimatedSteps - this.completedSteps;
                const estimatedRemaining = stepsPerSecond > 0 ? Math.floor(remainingSteps / stepsPerSecond) : 0;

                const etaEl = document.getElementById('progressETA');
                if (etaEl) {
                    if (estimatedRemaining > 0) {
                        if (estimatedRemaining < 60) {
                            etaEl.textContent = `ETA: ${estimatedRemaining}s`;
                        } else if (estimatedRemaining < 3600) {
                            const minutes = Math.floor(estimatedRemaining / 60);
                            etaEl.textContent = `ETA: ${minutes}m`;
                        } else {
                            const hours = Math.floor(estimatedRemaining / 3600);
                            const minutes = Math.floor((estimatedRemaining % 3600) / 60);
                            etaEl.textContent = `ETA: ${hours}h ${minutes}m`;
                        }
                    } else {
                        etaEl.textContent = 'Finishing up...';
                    }
                }
            }
        }
    }

    async fetchWithTimeout(url, options = {}) {
        const { timeout, ...fetchOptions } = options;

        // If no timeout specified, just do a regular fetch
        if (!timeout) {
            return fetch(url, fetchOptions);
        }

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout);

        try {
            const response = await fetch(url, {
                ...fetchOptions,
                signal: controller.signal
            });

            clearTimeout(timeoutId);
            return response;
        } catch (error) {
            clearTimeout(timeoutId);
            throw error;
        }
    }

    formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    renderPriorityDistribution(distribution) {
        if (!distribution) return '<p>No data available</p>';

        let html = '';
        const total = Object.values(distribution).reduce((a, b) => a + b, 0);

        ['High', 'Medium', 'Low'].forEach(level => {
            const count = distribution[level] || 0;
            const percentage = total > 0 ? ((count / total) * 100).toFixed(1) : 0;

            html += `
            <div class="mb-2">
                <div class="d-flex justify-content-between">
                    <span>${level}</span>
                    <span>${count} (${percentage}%)</span>
                </div>
                <div class="progress" style="height: 8px;">
                    <div class="progress-bar bg-${level === 'High' ? 'danger' :
                level === 'Medium' ? 'warning' : 'success'}" 
                         style="width: ${percentage}%"></div>
                </div>
            </div>
        `;
        });

        return html;
    }

    showToast(message, type = 'info') {
        try {
            const toastId = 'toast-' + Date.now();
            const toastHtml = `
            <div id="${toastId}" class="toast align-items-center text-white bg-${type} border-0" role="alert">
                <div class="d-flex">
                    <div class="toast-body">
                        ${message}
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
                </div>
            </div>
        `;

            const toastContainer = document.getElementById('toastContainer');
            if (toastContainer) {
                toastContainer.insertAdjacentHTML('beforeend', toastHtml);
                const toastElement = document.getElementById(toastId);
                if (toastElement) {
                    const toast = new bootstrap.Toast(toastElement, { delay: 3000 });
                    toast.show();

                    // Remove after hiding
                    toastElement.addEventListener('hidden.bs.toast', () => {
                        toastElement.remove();
                    });
                }
            } else {
                // Fallback to alert if toast container doesn't exist
                console.log(`[TOAST:${type}] ${message}`);
            }
        } catch (error) {
            console.error('Error showing toast:', error);
        }
    }

    escapeHtml(text) {
        if (typeof text !== 'string') return String(text);
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ========== CONTROL FUNCTIONS ==========
    stopAll() {
        if (this.abortController) {
            this.abortController.abort();
            this.log('INFO', 'Current operation cancelled');
        }

        this.activeRequests.forEach(controller => controller.abort());
        this.activeRequests.clear();

        this.showProgress(false);
        this.showToast('All operations stopped', 'warning');
    }

    skipAIEnhancement() {
        this.log('WARN', 'AI enhancement skipped by user');
        this.showToast('AI enhancement will be skipped on next generation', 'info');
        const aiMode = document.getElementById('aiMode');
        if (aiMode) aiMode.value = 'fast';
    }

    cancelRequest() {
        this.stopAll();
    }

    testUrls() {
        const testUrls = [
            'https://www.bbc.com',
            'https://example.com',
            'https://demo.playwright.dev',
            'https://github.com'
        ];

        const select = document.getElementById('targetUrl');
        if (!select) return;

        const currentIndex = testUrls.indexOf(select.value);
        const nextIndex = (currentIndex + 1) % testUrls.length;
        select.value = testUrls[nextIndex];

        this.log('INFO', 'Cycled test URL', { newUrl: select.value });
    }

    viewRawData() {
        const container = document.getElementById('rawDataContainer');
        if (!container) return;

        if (this.rawData) {
            container.textContent = JSON.stringify(this.rawData, null, 2);
        } else {
            container.textContent = 'No raw data available';
        }
    }

    copyRawData() {
        if (this.rawData) {
            navigator.clipboard.writeText(JSON.stringify(this.rawData, null, 2))
                .then(() => this.showToast('Raw data copied to clipboard', 'success'))
                .catch(err => this.log('ERROR', 'Failed to copy raw data', err));
        }
    }

    copyTestCase(index) {
        if (!this.rawData || !this.rawData.testCases || !this.rawData.testCases[index]) {
            this.showToast('Test case not found', 'warning');
            return;
        }

        const testCase = this.rawData.testCases[index];
        const text = this.formatTestCaseForCopy(testCase);

        navigator.clipboard.writeText(text)
            .then(() => this.showToast('Test case copied to clipboard', 'success'))
            .catch(err => {
                this.log('ERROR', 'Failed to copy test case', err);
                this.showToast('Failed to copy', 'danger');
            });
    }

    formatTestCaseForCopy(testCase) {
        return `Test Case: ${testCase.title || 'Untitled'}
Priority: ${testCase.priority || 'Medium'}
${testCase.description ? `Description: ${testCase.description}\n` : ''}
${testCase.precondition ? `Precondition: ${testCase.precondition}\n` : ''}
Steps:
${(testCase.steps || []).map((step, i) => `${i + 1}. ${step}`).join('\n')}

Expected Results:
${(testCase.expectedResults || []).map((result, i) => `${i + 1}. ${result}`).join('\n')}

${testCase.tags && testCase.tags.length ? `Tags: ${testCase.tags.join(', ')}` : ''}`;
    }

    downloadTestCases() {
        if (!this.rawData?.testCases) {
            this.showToast('No test cases to download', 'warning');
            return;
        }

        const content = this.rawData.testCases.map(tc =>
            tc.toHumanReadableFormat ? tc.toHumanReadableFormat() :
                JSON.stringify(tc, null, 2)
        ).join('\n\n' + '='.repeat(50) + '\n\n');

        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `test-cases-${new Date().toISOString().split('T')[0]}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        this.showToast('Test cases downloaded', 'success');
    }

    bindEvents() {
        // Bind enter key to generate tests
        const urlInput = document.getElementById('targetUrl');
        if (urlInput) {
            urlInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    this.generateTests();
                }
            });
        }

        // Toggle debug logging
        const debugToggle = document.getElementById('enableDebugLogging');
        if (debugToggle) {
            debugToggle.addEventListener('change', (e) => {
                this.enableDebugging = e.target.checked;
                this.log('INFO', `Debug logging ${this.enableDebugging ? 'enabled' : 'disabled'}`);
            });
        }

        // Update active requests counter
        setInterval(() => {
            const activeRequestsEl = document.getElementById('activeRequests');
            if (activeRequestsEl) {
                const count = this.activeRequests.size;
                activeRequestsEl.textContent =
                    `${count} active request${count !== 1 ? 's' : ''}`;
            }
        }, 1000);
    }
}

// Initialize the debugger when page loads
document.addEventListener('DOMContentLoaded', () => {
    try {
        window.app = new AutomationDebugger();

        // Global functions for button clicks
        window.testConnection = () => app.testBackendConnection().catch(console.error);
        window.testAI = () => app.testAI().catch(console.error);
        window.analyzeWebsite = () => app.analyzeWebsite();
        window.generateTests = () => app.generateTests();
        window.generatePlaywright = () => app.generatePlaywright();
        window.copyPlaywright = () => app.copyPlaywright();
        window.downloadPlaywright = () => app.downloadPlaywright();
        window.stopAll = () => app.stopAll();
        window.cancelRequest = () => app.cancelRequest();
        window.skipAIEnhancement = () => app.skipAIEnhancement();
        window.clearDebugLog = () => app.clearDebugLog();
        window.testUrls = () => app.testUrls();
        window.viewRawData = () => app.viewRawData();
        window.copyRawData = () => app.copyRawData();
        window.copyTestCases = () => app.copyTestCases();
        window.downloadTestCases = () => app.downloadTestCases();
        window.copyTestCase = (index) => app.copyTestCase(index);

        console.log('Automation Debugger initialized successfully');
    } catch (error) {
        console.error('Failed to initialize Automation Debugger:', error);
        alert('Error initializing application. Please check console for details.');
    }

    window.captureScreenshot = () => app.captureScreenshot();
    window.inspectElement = () => app.inspectElement();
    window.recordInteraction = () => app.recordInteraction();
});