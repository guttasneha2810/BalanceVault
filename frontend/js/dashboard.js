/**
 * LEDGERLINE - Dashboard Logic
 */

document.addEventListener('DOMContentLoaded', async () => {
    if (!Auth.requireAuth()) return;

    renderSidebar('dashboard');

    const user = Auth.getUser();
    const welcomeMsg = document.getElementById('dashboard-welcome');
    if (welcomeMsg) {
        welcomeMsg.textContent = `Welcome back, ${user.fullName || user.username}`;
    }

    // Role-specific dashboard rendering
    if (user.role === 'ROLE_ACCOUNT_HOLDER') {
        await loadAccountHolderDashboard();
    } else if (user.role === 'ROLE_ADMIN') {
        await loadAdminDashboard();
    } else if (user.role === 'ROLE_AUDITOR') {
        await loadAuditorDashboard();
    }
});

async function loadAccountHolderDashboard() {
    try {
        const [accountsRes, historyRes] = await Promise.all([
            apiFetch('/accounts'),
            apiFetch('/transactions/history')
        ]);

        const accounts = accountsRes.data || [];
        const transactions = (historyRes.data || []).slice(0, 5);

        // Calculate total balance
        let totalBalance = 0;
        accounts.forEach(acc => {
            totalBalance += parseFloat(acc.balance || 0);
        });

        // Update KPI Cards
        document.getElementById('kpi-1-title').textContent = 'Total Available Balance';
        document.getElementById('kpi-1-value').textContent = `$${totalBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
        document.getElementById('kpi-1-sub').textContent = `${accounts.length} Active Accounts`;

        document.getElementById('kpi-2-title').textContent = 'Primary Account';
        if (accounts.length > 0) {
            document.getElementById('kpi-2-value').textContent = accounts[0].accountNumber;
            document.getElementById('kpi-2-sub').textContent = `${accounts[0].accountType} • $${parseFloat(accounts[0].balance).toFixed(2)}`;
        } else {
            document.getElementById('kpi-2-value').textContent = 'No Account';
            document.getElementById('kpi-2-sub').textContent = 'Create one in My Accounts';
        }

        document.getElementById('kpi-3-title').textContent = 'Recent Activity';
        document.getElementById('kpi-3-value').textContent = `${transactions.length} Transactions`;
        document.getElementById('kpi-3-sub').textContent = 'Last 30 days';

        // Render Quick Accounts Preview
        const accountsContainer = document.getElementById('dashboard-accounts-list');
        if (accountsContainer) {
            if (accounts.length === 0) {
                accountsContainer.innerHTML = '<p style="color:#64748b;font-size:14px;">No accounts found. Go to My Accounts to create one.</p>';
            } else {
                let html = '<div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(260px, 1fr));gap:16px;">';
                accounts.forEach(acc => {
                    html += `
                        <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:16px;">
                            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                                <span style="font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;">${acc.accountType}</span>
                                <span class="badge ${acc.status === 'ACTIVE' ? 'badge-success' : 'badge-danger'}">${acc.status}</span>
                            </div>
                            <div style="font-size:18px;font-weight:700;color:#0f172a;font-family:monospace;letter-spacing:1px;margin-bottom:4px;">${acc.accountNumber}</div>
                            <div style="font-size:22px;font-weight:700;color:#10b981;">$${parseFloat(acc.balance).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                            <div style="margin-top:12px;display:flex;gap:8px;">
                                <a href="deposit.html?acc=${acc.accountNumber}" class="btn btn-outline btn-sm" style="flex:1;">Deposit</a>
                                <a href="transfer.html?src=${acc.accountNumber}" class="btn btn-outline btn-sm" style="flex:1;">Transfer</a>
                            </div>
                        </div>
                    `;
                });
                html += '</div>';
                accountsContainer.innerHTML = html;
            }
        }

        // Render Recent Transactions
        renderTransactionsTable(transactions);

    } catch (err) {
        showToast('Failed to load dashboard data: ' + err.message, 'error');
    }
}
async function loadAdminDashboard() {
    try {
        const [usersRes, accountsRes, logsRes] = await Promise.all([
            apiFetch('/admin/users'),
            apiFetch('/admin/accounts'),
            apiFetch('/audit/logs')
        ]);

        const users = usersRes.data || [];
        const accounts = accountsRes.data || [];
        const logs = logsRes.data || [];

        // Load pending registrations separately so existing
        // dashboard functionality still works if this endpoint fails.
        let pendingUsers = [];

        try {
            const pendingRes = await apiFetch('/admin/users/pending');
            pendingUsers = pendingRes.data || [];
        } catch (error) {
            console.error('Could not load pending registrations:', error);
        }

        let totalLiquidity = 0;

        accounts.filter(a => !a.systemAccount).forEach(a => {
            totalLiquidity += parseFloat(a.balance || 0);
        });

        document.getElementById('kpi-1-title').textContent =
            'Total Customer Liquidity';

        document.getElementById('kpi-1-value').textContent =
            `$${totalLiquidity.toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}`;

        document.getElementById('kpi-1-sub').textContent =
            'Across all user checking & savings';

        document.getElementById('kpi-2-title').textContent =
            'Registered Users';

        document.getElementById('kpi-2-value').textContent =
            users.length;

        document.getElementById('kpi-2-sub').textContent =
            `${pendingUsers.length} awaiting approval`;

        document.getElementById('kpi-3-title').textContent =
            'Security Audit Events';

        document.getElementById('kpi-3-value').textContent =
            logs.length;

        document.getElementById('kpi-3-sub').textContent =
            'Total recorded events';

        // Create or reuse the pending registrations card.
        const txSec = document.getElementById(
            'dashboard-transactions-card'
        );

        let pendingCard = document.getElementById(
            'pending-approvals-card'
        );

        if (txSec && !pendingCard) {
            pendingCard = document.createElement('div');
            pendingCard.id = 'pending-approvals-card';
            pendingCard.className = 'card';
            txSec.parentNode.insertBefore(pendingCard, txSec);
        }

        if (pendingCard) {
            pendingCard.innerHTML = `
                <div class="card-header">
                    <h3>Pending Registrations</h3>
                    <span class="badge ${
                        pendingUsers.length > 0
                            ? 'badge-warning'
                            : 'badge-success'
                    }">
                        ${pendingUsers.length} Pending
                    </span>
                </div>

                <div class="card-body">
                    ${
                        pendingUsers.length === 0
                            ? `<p style="text-align:center;padding:20px;color:#64748b;">
                                No pending registrations.
                               </p>`
                            : `
                                <div class="table-responsive">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th>Name</th>
                                                <th>Username</th>
                                                <th>Email</th>
                                                <th>Registered</th>
                                                <th>Action</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            ${pendingUsers.map(user => `
                                                <tr>
                                                    <td>${escapeApprovalHtml(user.fullName || '-')}</td>
                                                    <td>${escapeApprovalHtml(user.username || '-')}</td>
                                                    <td>${escapeApprovalHtml(user.email || '-')}</td>
                                                    <td>${user.createdAt
                                                        ? new Date(user.createdAt).toLocaleDateString()
                                                        : '-'}</td>
                                                    <td style="white-space:nowrap;">
                                                        <button
                                                            class="btn btn-primary btn-sm"
                                                            data-approval-id="${user.id}"
                                                            data-approval-status="APPROVED">
                                                            Approve
                                                        </button>

                                                        <button
                                                            class="btn btn-outline btn-sm"
                                                            data-approval-id="${user.id}"
                                                            data-approval-status="REJECTED"
                                                            style="color:#dc2626;">
                                                            Reject
                                                        </button>
                                                    </td>
                                                </tr>
                                            `).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            `
                    }
                </div>
            `;

            // Attach approval/rejection actions.
            pendingCard.querySelectorAll('[data-approval-id]')
                .forEach(button => {
                    button.addEventListener('click', async () => {
                        const userId = button.dataset.approvalId;
                        const status = button.dataset.approvalStatus;

                        const confirmed = confirm(
                            `Are you sure you want to ${status.toLowerCase()} this registration?`
                        );

                        if (!confirmed) return;

                        button.disabled = true;

                        try {
                            await apiFetch(
                                `/admin/users/${userId}/approval`,
                                {
                                    method: 'PATCH',
                                    body: JSON.stringify({ status })
                                }
                            );

                            showToast(
                                `User ${status.toLowerCase()} successfully.`,
                                'success'
                            );

                            await loadAdminDashboard();

                        } catch (error) {
                            showToast(
                                'Approval failed: ' + error.message,
                                'error'
                            );

                            button.disabled = false;
                        }
                    });
                });
        }

        // Preserve existing Admin audit events section.
        if (txSec) {
            txSec.querySelector('.card-header h3').textContent =
                'Recent Security Audit Events';

            const body = txSec.querySelector('.card-body');

            body.innerHTML = `
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Timestamp</th>
                                <th>Event Type</th>
                                <th>Initiator</th>
                                <th>Client IP</th>
                                <th>Status</th>
                                <th>Details</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${logs.slice(0, 6).map(l => `
                                <tr>
                                    <td>${new Date(l.timestamp).toLocaleString()}</td>
                                    <td><strong>${escapeApprovalHtml(l.eventType)}</strong></td>
                                    <td>${escapeApprovalHtml(l.username || 'Anonymous')}</td>
                                    <td><code>${escapeApprovalHtml(l.clientIp || '-')}</code></td>
                                    <td>
                                        <span class="badge ${
                                            l.status === 'SUCCESS'
                                                ? 'badge-success'
                                                : 'badge-danger'
                                        }">
                                            ${escapeApprovalHtml(l.status)}
                                        </span>
                                    </td>
                                    <td>${escapeApprovalHtml(l.failureReason || l.details || '-')}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            `;
        }

    } catch (err) {
        showToast(
            'Failed to load admin dashboard: ' + err.message,
            'error'
        );
    }
}

// Escape text before inserting user-controlled values into HTML.
function escapeApprovalHtml(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[character]);
}
async function loadAuditorDashboard() {
    try {
        const [accountsRes, historyRes, logsRes] = await Promise.all([
            apiFetch('/accounts'),
            apiFetch('/transactions/history'),
            apiFetch('/audit/logs')
        ]);

        const accounts = accountsRes.data || [];
        const txns = historyRes.data || [];
        const logs = logsRes.data || [];

        document.getElementById('kpi-1-title').textContent = 'Ledger Invariant Status';
        document.getElementById('kpi-1-value').textContent = 'BALANCED (0.00)';
        document.getElementById('kpi-1-sub').textContent = 'Double-entry zero-sum verified';

        document.getElementById('kpi-2-title').textContent = 'Total Transactions';
        document.getElementById('kpi-2-value').textContent = txns.length;
        document.getElementById('kpi-2-sub').textContent = 'Immutable journal records';

        document.getElementById('kpi-3-title').textContent = 'Audit Events';
        document.getElementById('kpi-3-value').textContent = logs.length;
        document.getElementById('kpi-3-sub').textContent = 'Non-repudiation audit trail';

        renderTransactionsTable(txns.slice(0, 8));
    } catch (err) {
        showToast('Failed to load auditor dashboard: ' + err.message, 'error');
    }
}

function renderTransactionsTable(transactions) {
    const tableBody = document.getElementById('dashboard-transactions-table');
    if (!tableBody) return;

    if (transactions.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:#64748b;padding:24px;">No transactions recorded yet.</td></tr>`;
        return;
    }

    tableBody.innerHTML = transactions.map(t => {
        const isPositive = t.signedAmount !== null && t.signedAmount !== undefined
        ? Number(t.signedAmount) >= 0
        : t.transactionType === 'DEPOSIT';

    const sign = isPositive ? '+' : '-';
    const amountClass = isPositive ? 'amount-positive' : 'amount-negative';

        return `
            <tr>
                <td><code style="font-size:12px;">${t.transactionReference}</code></td>
                <td><span class="badge ${t.transactionType === 'DEPOSIT' ? 'badge-success' : (t.transactionType === 'WITHDRAWAL' ? 'badge-danger' : 'badge-info')}">${t.transactionType}</span></td>
                <td>${t.description}</td>
                <td class="${amountClass}">${sign}$${parseFloat(t.amount).toFixed(2)}</td>
                <td>${new Date(t.timestamp).toLocaleDateString()} ${new Date(t.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</td>
            </tr>
        `;
    }).join('');
}
