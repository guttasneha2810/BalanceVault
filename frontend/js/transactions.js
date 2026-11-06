/**
 * LEDGERLINE - Financial Transactions, Statements & Audit Logs
 */

document.addEventListener('DOMContentLoaded', async () => {
    if (!Auth.requireAuth()) return;

    // Detect current page from URL
    const pathname = window.location.pathname;
    const page = pathname.substring(pathname.lastIndexOf('/') + 1).replace('.html', '');

    renderSidebar(page);

    if (page === 'deposit') {
        initDepositPage();
    } else if (page === 'withdraw') {
        initWithdrawPage();
    } else if (page === 'transfer') {
        initTransferPage();
    } else if (page === 'transactions') {
        loadTransactionsHistory();
    } else if (page === 'statement') {
        initStatementPage();
    } else if (page === 'audit') {
        loadAuditLogs();
    }
});

// Helper to generate UUID v4 for Idempotency-Key header
function generateIdempotencyKey() {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID();
    }
    return 'idemp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 11);
}

// =========================================================================
// DEPOSIT MONEY PAGE
// =========================================================================
async function initDepositPage() {
    const form = document.getElementById('form-deposit');
    const accountSelect = document.getElementById('deposit-account');
    if (!form || !accountSelect) return;

    // Populate user accounts
    await populateUserAccountsDropdown(accountSelect);

    // Preselect if query param exists
    const urlParams = new URLSearchParams(window.location.search);
    const preselect = urlParams.get('acc');
    if (preselect) {
        accountSelect.value = preselect;
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const account = accountSelect.value;
        const amount = parseFloat(document.getElementById('deposit-amount').value);
        const description = document.getElementById('deposit-desc').value.trim() || 'Funds Deposit';

        if (!account) {
            showToast('Please select a target account', 'error');
            return;
        }
        if (isNaN(amount) || amount <= 0) {
            showToast('Deposit amount must be strictly greater than $0.00', 'error');
            return;
        }

        const detailsHtml = `
            <strong>Target Account:</strong> ${account}<br/>
            <strong>Amount:</strong> $${amount.toFixed(2)} USD<br/>
            <strong>Description:</strong> ${description}<br/>
            <strong>Clearing Channel:</strong> System External Deposit Clearing
        `;

        showConfirmModal(
            'Confirm Deposit',
            'Are you sure you want to deposit funds into this account? This will create an immutable double-entry ledger record.',
            detailsHtml,
            async () => {
                await executeDeposit(account, amount, description);
            }
        );
    });
}

async function executeDeposit(accountNumber, amount, description) {
    const submitBtn = document.getElementById('btn-submit-deposit');
    try {
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Processing Deposit...';
        }

        const idempotencyKey = generateIdempotencyKey();

        const res = await apiFetch('/transactions/deposit', {
            method: 'POST',
            headers: {
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify({
                accountNumber,
                amount,
                description
            })
        });

        showToast(`Deposit Successful! Reference: ${res.data.transactionReference}`, 'success');
        setTimeout(() => {
            window.location.href = 'accounts.html';
        }, 1500);

    } catch (err) {
        showToast('Deposit Refused: ' + err.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Deposit Funds';
        }
    }
}

// =========================================================================
// WITHDRAW MONEY PAGE
// =========================================================================
async function initWithdrawPage() {
    const form = document.getElementById('form-withdraw');
    const accountSelect = document.getElementById('withdraw-account');
    if (!form || !accountSelect) return;

    await populateUserAccountsDropdown(accountSelect);

    const urlParams = new URLSearchParams(window.location.search);
    const preselect = urlParams.get('acc');
    if (preselect) {
        accountSelect.value = preselect;
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const account = accountSelect.value;
        const amount = parseFloat(document.getElementById('withdraw-amount').value);
        const description = document.getElementById('withdraw-desc').value.trim() || 'Funds Withdrawal';

        if (!account) {
            showToast('Please select an account', 'error');
            return;
        }
        if (isNaN(amount) || amount <= 0) {
            showToast('Withdrawal amount must be strictly greater than $0.00', 'error');
            return;
        }

        const detailsHtml = `
            <strong>Source Account:</strong> ${account}<br/>
            <strong>Amount:</strong> $${amount.toFixed(2)} USD<br/>
            <strong>Description:</strong> ${description}<br/>
            <strong>Clearing Channel:</strong> System External Withdrawal Clearing
        `;

        showConfirmModal(
            'Confirm Withdrawal',
            'Are you sure you want to withdraw funds? An atomic balance verification will be performed under row lock.',
            detailsHtml,
            async () => {
                await executeWithdraw(account, amount, description);
            }
        );
    });
}

async function executeWithdraw(accountNumber, amount, description) {
    const submitBtn = document.getElementById('btn-submit-withdraw');
    try {
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Processing Withdrawal...';
        }

        const idempotencyKey = generateIdempotencyKey();

        const res = await apiFetch('/transactions/withdraw', {
            method: 'POST',
            headers: {
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify({
                accountNumber,
                amount,
                description
            })
        });

        showToast(`Withdrawal Successful! Reference: ${res.data.transactionReference}`, 'success');
        setTimeout(() => {
            window.location.href = 'accounts.html';
        }, 1500);

    } catch (err) {
        showToast('Withdrawal Refused: ' + err.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Withdraw Funds';
        }
    }
}

// =========================================================================
// TRANSFER MONEY PAGE
// =========================================================================
async function initTransferPage() {
    const form = document.getElementById('form-transfer');
    const sourceSelect = document.getElementById('transfer-source');
    if (!form || !sourceSelect) return;

    await populateUserAccountsDropdown(sourceSelect);

    const urlParams = new URLSearchParams(window.location.search);
    const preselect = urlParams.get('src');
    if (preselect) {
        sourceSelect.value = preselect;
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const source = sourceSelect.value;
        const destination = document.getElementById('transfer-dest').value.trim();
        const amount = parseFloat(document.getElementById('transfer-amount').value);
        const description = document.getElementById('transfer-desc').value.trim() || 'Internal Account Transfer';

        if (!source) {
            showToast('Please select a source account', 'error');
            return;
        }
        if (!destination) {
            showToast('Please enter a destination account number', 'error');
            return;
        }
        if (source === destination) {
            showToast('Source and destination accounts cannot be identical', 'error');
            return;
        }
        if (isNaN(amount) || amount <= 0) {
            showToast('Transfer amount must be strictly greater than $0.00', 'error');
            return;
        }

        const detailsHtml = `
            <strong>Source Account:</strong> ${source}<br/>
            <strong>Destination Account:</strong> ${destination}<br/>
            <strong>Transfer Amount:</strong> $${amount.toFixed(2)} USD<br/>
            <strong>Description:</strong> ${description}
        `;

        showConfirmModal(
            'Confirm Wire Transfer',
            'Are you sure you want to execute this transfer? Both accounts will be locked in ascending ID order to guarantee consistency and deadlock prevention.',
            detailsHtml,
            async () => {
                await executeTransfer(source, destination, amount, description);
            }
        );
    });
}

async function executeTransfer(sourceAccountNumber, destinationAccountNumber, amount, description) {
    const submitBtn = document.getElementById('btn-submit-transfer');
    try {
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Processing Transfer...';
        }

        const idempotencyKey = generateIdempotencyKey();

        const res = await apiFetch('/transactions/transfer', {
            method: 'POST',
            headers: {
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify({
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                description
            })
        });

        showToast(`Transfer Completed! Reference: ${res.data.transactionReference}`, 'success');
        setTimeout(() => {
            window.location.href = 'transactions.html';
        }, 1500);

    } catch (err) {
        showToast('Transfer Refused: ' + err.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Transfer Money';
        }
    }
}

// =========================================================================
// TRANSACTIONS HISTORY PAGE
// =========================================================================
async function loadTransactionsHistory() {
    const tableBody = document.getElementById('transactions-table-body');
    if (!tableBody) return;

    tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:24px;">Loading transactions...</td></tr>';

    try {
        const res = await apiFetch('/transactions/history');
        const txns = res.data || [];

        if (txns.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#64748b;padding:24px;">No transaction history found.</td></tr>';
            return;
        }

        tableBody.innerHTML = txns.map(t => {
            const isDeposit = t.transactionType === 'DEPOSIT';
            const sign = isDeposit ? '+' : '-';
            const amountClass = isDeposit ? 'amount-positive' : 'amount-negative';

            return `
                <tr>
                    <td><code style="font-weight:600;font-size:12px;">${t.transactionReference}</code></td>
                    <td><span class="badge ${t.transactionType === 'DEPOSIT' ? 'badge-success' : (t.transactionType === 'WITHDRAWAL' ? 'badge-danger' : 'badge-info')}">${t.transactionType}</span></td>
                    <td>${t.description}</td>
                    <td class="${amountClass}">${sign}$${parseFloat(t.amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                    <td><span class="badge badge-success">${t.status}</span></td>
                    <td>${new Date(t.timestamp).toLocaleDateString()} ${new Date(t.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</td>
                </tr>
            `;
        }).join('');

    } catch (err) {
        showToast('Failed to load transaction history: ' + err.message, 'error');
    }
}

// =========================================================================
// ACCOUNT STATEMENTS PAGE
// =========================================================================
async function initStatementPage() {
    const accountSelect = document.getElementById('statement-account');
    const form = document.getElementById('form-statement');
    if (!accountSelect || !form) return;

    await populateUserAccountsDropdown(accountSelect);

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const account = accountSelect.value;
        const startDate = document.getElementById('statement-start-date').value;
        const endDate = document.getElementById('statement-end-date').value;

        if (!account) {
            showToast('Please select an account', 'error');
            return;
        }

        await generateStatement(account, startDate, endDate);
    });
}

async function generateStatement(accountNumber, startDate, endDate) {
    const resultsContainer = document.getElementById('statement-results');
    const entriesTable = document.getElementById('statement-entries-body');
    if (!resultsContainer || !entriesTable) return;

    try {
        let endpoint = `/statements/account/${accountNumber}`;
        const params = [];
        if (startDate) params.push(`startDate=${startDate}`);
        if (endDate) params.push(`endDate=${endDate}`);
        if (params.length > 0) endpoint += `?${params.join('&')}`;

        const res = await apiFetch(endpoint);
        const data = res.data;

        document.getElementById('stmt-acc-num').textContent = data.accountNumber;
        document.getElementById('stmt-acc-name').textContent = data.accountName;
        document.getElementById('stmt-balance').textContent = `$${parseFloat(data.currentBalance).toFixed(2)} ${data.currency}`;
        document.getElementById('stmt-period').textContent = `${data.startDate || 'Start'} to ${data.endDate || 'End'}`;

        const entries = data.entries || [];
        if (entries.length === 0) {
            entriesTable.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#64748b;padding:24px;">No ledger entries found for this period.</td></tr>';
        } else {
            entriesTable.innerHTML = entries.map(e => {
                const isCredit = e.entryType === 'CREDIT';
                const sign = isCredit ? '+' : '-';
                const colorClass = isCredit ? 'amount-positive' : 'amount-negative';

                return `
                    <tr>
                        <td>${new Date(e.timestamp).toLocaleDateString()} ${new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</td>
                        <td><code>${e.transactionReference}</code></td>
                        <td>${e.description}</td>
                        <td><span class="badge ${isCredit ? 'badge-success' : 'badge-danger'}">${e.entryType}</span></td>
                        <td class="${colorClass}">${sign}$${parseFloat(e.amount).toFixed(2)}</td>
                        <td style="font-weight:700;">$${parseFloat(e.balanceAfter).toFixed(2)}</td>
                    </tr>
                `;
            }).join('');
        }

        resultsContainer.style.display = 'block';

    } catch (err) {
        showToast('Failed to generate statement: ' + err.message, 'error');
    }
}

// =========================================================================
// AUDIT LOGS PAGE (ADMIN & AUDITOR ONLY)
// =========================================================================
async function loadAuditLogs() {
    const tableBody = document.getElementById('audit-table-body');
    if (!tableBody) return;

    tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;">Loading audit trail...</td></tr>';

    try {
        const res = await apiFetch('/audit/logs');
        const logs = res.data || [];

        if (logs.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#64748b;padding:24px;">No audit events recorded.</td></tr>';
            return;
        }

        tableBody.innerHTML = logs.map(l => {
            const isSuccess = l.status === 'SUCCESS';
            return `
                <tr>
                    <td style="font-size:12px;">${new Date(l.timestamp).toLocaleString()}</td>
                    <td><strong>${l.eventType}</strong></td>
                    <td>${l.username || 'Anonymous'}</td>
                    <td><code>${l.clientIp || '-'}</code></td>
                    <td>${l.accountNumber || '-'}</td>
                    <td><span class="badge ${isSuccess ? 'badge-success' : 'badge-danger'}">${l.status}</span></td>
                    <td style="font-size:12px;color:${isSuccess ? '#475569' : '#dc2626'};">${l.failureReason || l.details || '-'}</td>
                </tr>
            `;
        }).join('');

    } catch (err) {
        showToast('Failed to load audit logs: ' + err.message, 'error');
    }
}

// Helper: Populate account dropdowns
async function populateUserAccountsDropdown(selectEl) {
    try {
        const res = await apiFetch('/accounts');
        const accounts = res.data || [];

        selectEl.innerHTML = '<option value="">-- Select Account --</option>';
        accounts.filter(a => !a.systemAccount && a.status === 'ACTIVE').forEach(acc => {
            const opt = document.createElement('option');
            opt.value = acc.accountNumber;
            opt.textContent = `${acc.accountNumber} - ${acc.accountName} ($${parseFloat(acc.balance).toFixed(2)})`;
            selectEl.appendChild(opt);
        });
    } catch (err) {
        console.error('Failed to populate accounts:', err);
    }
}
