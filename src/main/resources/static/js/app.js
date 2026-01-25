// Configuration
const API_BASE_URL = 'http://localhost:8080/api/v1';
let currentSessionId = null;
let currentScriptFilename = null;

// Initialize
document.addEventListener('DOMContentLoaded', function() {
    checkHealth();
    loadSessions();
});

// Health Check
async function checkHealth() {
    try {
        const response = await fetch(`${API_BASE_URL}/health`);
        const data = await response.json();

        const statusEl = document.getElementById('healthStatus');
        if (data.status === 'UP') {
            statusEl.innerHTML = `<span class="badge bg-success">✓ SYSTEM UP</span>`;
            document.getElementById('apiInfo').innerHTML =
                `Version: ${data.version} | Active Sessions: ${data.activeSessions}`;
        } else {
            statusEl.innerHTML = `<span class="badge bg-danger">✗ SYSTEM DOWN</span>`;
        }
    } catch (error) {
        console.error('Health check failed:', error);
        document.getElementById('healthStatus').innerHTML =
            `<span class="badge bg-warning">⚠ CONNECTION ERROR</span>`;
    }
}

// Start Recording
async function startRecording() {
    const sessionName = document.getElementById('sessionName').value.trim();
    const targetUrl = document.getElementById('targetUrl').value.trim();
    const headlessMode = document.getElementById('headlessMode').checked;

    if (!sessionName || !targetUrl) {
        alert('Please enter both session name and URL');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/recordings`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: targetUrl,
                sessionName: sessionName,
                headless: headlessMode
            })
        });

        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const data = await response.json();
        currentSessionId = data.sessionId;

        // Update UI
        document.getElementById('currentSessionId').textContent = currentSessionId;
        document.getElementById('sessionInfo').style.display = 'block';
        document.getElementById('recordingIndicator').style.display = 'inline-block';
        document.getElementById('btnStartRecording').disabled = true;
        document.getElementById('btnStopRecording').disabled = false;
        document.getElementById('btnAddTestActions').disabled = false;
        document.getElementById('btnGenerateScript').disabled = false;

        // Load session details
        loadSessionDetails();
        updateActionCount();

        alert(`Recording started! Session ID: ${currentSessionId}`);

    } catch (error) {
        console.error('Failed to start recording:', error);
        alert(`Error starting recording: ${error.message}`);
    }
}

// Stop Recording
async function stopRecording() {
    if (!currentSessionId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/recordings/${currentSessionId}/stop`, {
            method: 'DELETE'
        });

        if (response.ok) {
            // Update UI
            document.getElementById('recordingIndicator').style.display = 'none';
            document.getElementById('btnStartRecording').disabled = false;
            document.getElementById('btnStopRecording').disabled = true;
            document.getElementById('sessionStatus').textContent = 'Stopped';

            alert('Recording stopped!');
            loadSessions(); // Refresh session list
        }
    } catch (error) {
        console.error('Failed to stop recording:', error);
        alert(`Error stopping recording: ${error.message}`);
    }
}

// Add Test Actions
async function addTestActions() {
    if (!currentSessionId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/recordings/${currentSessionId}/test-actions`, {
            method: 'POST'
        });

        if (response.ok) {
            alert('Test actions added successfully!');
            loadSessionDetails();
            updateActionCount();
        }
    } catch (error) {
        console.error('Failed to add test actions:', error);
        alert(`Error adding test actions: ${error.message}`);
    }
}

// Generate Script
async function generateScript() {
    if (!currentSessionId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/scripts/generate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                sessionId: currentSessionId,
                includeAssertions: true,
                addComments: true
            })
        });

        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const data = await response.json();
        currentScriptFilename = data.filename;

        // Display script
        document.getElementById('scriptOutput').innerHTML =
            `<div class="code-block">${escapeHtml(data.script)}</div>`;
        document.getElementById('scriptActions').style.display = 'block';

        // Show success message
        alert(`Script generated: ${data.filename}\nActions: ${data.actionCount}`);

    } catch (error) {
        console.error('Failed to generate script:', error);
        alert(`Error generating script: ${error.message}`);
    }
}

// Load Session Details
async function loadSessionDetails() {
    if (!currentSessionId) return;

    try {
        // Get session info
        const sessionResponse = await fetch(`${API_BASE_URL}/recordings/${currentSessionId}`);
        if (sessionResponse.ok) {
            const session = await sessionResponse.json();
            document.getElementById('actionCount').textContent = session.actions ? session.actions.length : 0;

            // Display actions
            if (session.actions && session.actions.length > 0) {
                const actionsHtml = session.actions.map((action, index) => `
                    <div class="action-item">
                        <strong>${index + 1}. ${action.type}</strong><br>
                        <small>Selector: ${action.selector || 'N/A'}</small><br>
                        <small>Value: ${action.value || 'N/A'}</small>
                    </div>
                `).join('');
                document.getElementById('actionsList').innerHTML = actionsHtml;
            }
        }

        // Get events
        const eventsResponse = await fetch(`${API_BASE_URL}/recordings/${currentSessionId}/events`);
        if (eventsResponse.ok) {
            const events = await eventsResponse.json();
            console.log('Session events:', events);
        }

    } catch (error) {
        console.error('Failed to load session details:', error);
    }
}

// Update Action Count
async function updateActionCount() {
    if (!currentSessionId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/recordings/${currentSessionId}`);
        if (response.ok) {
            const session = await response.json();
            const count = session.actions ? session.actions.length : 0;
            document.getElementById('actionCount').textContent = count;
        }
    } catch (error) {
        console.error('Failed to update action count:', error);
    }
}

// Load All Sessions
async function loadSessions() {
    try {
        const response = await fetch(`${API_BASE_URL}/recordings`);
        if (response.ok) {
            const sessionIds = await response.json();
            const sessionsList = document.getElementById('sessionsList');

            if (sessionIds.length === 0) {
                sessionsList.innerHTML = '<p class="text-muted">No previous sessions found.</p>';
                return;
            }

            // Fetch details for each session
            const sessionPromises = sessionIds.map(async (id) => {
                try {
                    const sessionRes = await fetch(`${API_BASE_URL}/recordings/${id}`);
                    if (sessionRes.ok) {
                        return await sessionRes.json();
                    }
                } catch (e) {
                    console.error(`Failed to fetch session ${id}:`, e);
                }
                return null;
            });

            const sessions = (await Promise.all(sessionPromises)).filter(s => s !== null);

            // Display sessions
            const sessionsHtml = sessions.map(session => `
                <div class="card mb-2">
                    <div class="card-body p-3">
                        <h6 class="card-title">${session.sessionName || 'Unnamed Session'}</h6>
                        <p class="card-text mb-1">
                            <small>ID: <code>${session.id.substring(0, 8)}...</code></small><br>
                            <small>URL: ${session.url}</small><br>
                            <small>Actions: ${session.actions ? session.actions.length : 0}</small><br>
                            <small>Started: ${new Date(session.startTime).toLocaleString()}</small>
                        </p>
                        <button onclick="loadSession('${session.id}')" class="btn btn-sm btn-outline-primary">Load</button>
                        <button onclick="deleteSession('${session.id}')" class="btn btn-sm btn-outline-danger">Delete</button>
                    </div>
                </div>
            `).join('');

            sessionsList.innerHTML = sessionsHtml;
        }
    } catch (error) {
        console.error('Failed to load sessions:', error);
        document.getElementById('sessionsList').innerHTML =
            '<p class="text-danger">Error loading sessions</p>';
    }
}

// Load Specific Session
async function loadSession(sessionId) {
    currentSessionId = sessionId;

    // Update UI
    document.getElementById('currentSessionId').textContent = currentSessionId;
    document.getElementById('sessionInfo').style.display = 'block';
    document.getElementById('btnStartRecording').disabled = true;
    document.getElementById('btnStopRecording').disabled = false;
    document.getElementById('btnAddTestActions').disabled = false;
    document.getElementById('btnGenerateScript').disabled = false;

    loadSessionDetails();
}

// Delete Session
async function deleteSession(sessionId) {
    if (!confirm('Are you sure you want to delete this session?')) return;

    try {
        const response = await fetch(`${API_BASE_URL}/recordings/${sessionId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            alert('Session deleted!');
            loadSessions(); // Refresh list

            // If current session was deleted, reset UI
            if (sessionId === currentSessionId) {
                currentSessionId = null;
                document.getElementById('sessionInfo').style.display = 'none';
                document.getElementById('btnStartRecording').disabled = false;
                document.getElementById('btnStopRecording').disabled = true;
                document.getElementById('btnAddTestActions').disabled = true;
                document.getElementById('btnGenerateScript').disabled = true;
            }
        }
    } catch (error) {
        console.error('Failed to delete session:', error);
        alert(`Error deleting session: ${error.message}`);
    }
}

// Download Script
async function downloadScript() {
    if (!currentScriptFilename) return;

    try {
        const response = await fetch(`${API_BASE_URL}/scripts/${currentScriptFilename}`);
        if (response.ok) {
            const script = await response.text();

            // Create download link
            const blob = new Blob([script], { type: 'application/javascript' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = currentScriptFilename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }
    } catch (error) {
        console.error('Failed to download script:', error);
        alert(`Error downloading script: ${error.message}`);
    }
}

// Copy Script to Clipboard
async function copyToClipboard() {
    if (!currentScriptFilename) return;

    try {
        const response = await fetch(`${API_BASE_URL}/scripts/${currentScriptFilename}`);
        if (response.ok) {
            const script = await response.text();
            await navigator.clipboard.writeText(script);
            alert('Script copied to clipboard!');
        }
    } catch (error) {
        console.error('Failed to copy script:', error);
        alert(`Error copying script: ${error.message}`);
    }
}

// Utility function to escape HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Auto-refresh session details every 5 seconds when recording
setInterval(() => {
    if (currentSessionId) {
        updateActionCount();
    }
}, 5000);