/**
 * LEDGERLINE - Account Management Logic
 */

document.addEventListener('DOMContentLoaded', async () => {
    if (!Auth.requireAuth()) return;

    renderSidebar('accounts');
    await loadAccounts();

    const createForm = document.getElementById('form-create-account');
    if (createForm) {
        createForm.addEventListener('submit', handleCreateAccount);
    }
});

async function loadAccounts() {
    const listContainer = document.getElementById('accounts-table-body');
    if (!listContainer) return;

    listContainer.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;">Loading accounts...</td></tr>';

    try {
        const user = Auth.getUser();
        let endpoint = '/accounts';
        if (user.role === 'ROLE_ADMIN') {
            endpoint = '/admin/accounts';
        }

        const res = await apiFetch(endpoint);
        const accounts = res.data || [];

        if (accounts.length === 0) {
            listContainer.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#64748b;padding:24px;">No accounts found.</td></tr>';
            return;
        }

        listContainer.innerHTML = accounts.map(acc => {
            const isSystem = acc.systemAccount;
            const statusClass = acc.status === 'ACTIVE' ? 'badge-success' : (acc.status === 'FROZEN' ? 'badge-warning' : 'badge-danger');
            
            let actionsHtml = '';
            if (user.role === 'ROLE_ACCOUNT_HOLDER' && !isSystem) {
                actionsHtml = `
                    <div style="display:flex;gap:6px;">
                        <a href="deposit.html?acc=${acc.accountNumber}" class="btn btn-outline btn-sm">Deposit</a>
                        <a href="withdraw.html?acc=${acc.accountNumber}" class="btn btn-outline btn-sm">Withdraw</a>
                        <a href="transfer.html?src=${acc.accountNumber}" class="btn btn-outline btn-sm">Transfer</a>
                    </div>
                `;
            } else if (user.role === 'ROLE_ADMIN' && !isSystem) {
                actionsHtml = `
                    <select onchange="updateAccountStatus(${acc.id}, this.value)" class="form-control" style="padding:4px 8px;font-size:12px;width:auto;">
                        <option value="ACTIVE" ${acc.status === 'ACTIVE' ? 'selected' : ''}>ACTIVE</option>
                        <option value="FROZEN" ${acc.status === 'FROZEN' ? 'selected' : ''}>FROZEN</option>
                        <option value="CLOSED" ${acc.status === 'CLOSED' ? 'selected' : ''}>CLOSED</option>
                    </select>
                `;
            } else {
                actionsHtml = '<span style="color:#94a3b8;font-size:12px;">Read-only</span>';
            }

            return `
                <tr>
                    <td><strong style="font-family:monospace;font-size:14px;">${acc.accountNumber}</strong></td>
                    <td>${acc.accountName}</td>
                    <td><span class="badge badge-neutral">${acc.accountType}</span></td>
                    <td>${acc.ownerName || 'SYSTEM'}</td>
                    <td style="font-weight:700;font-variant-numeric:tabular-nums;color:${parseFloat(acc.balance) >= 0 ? '#10b981' : '#ef4444'};">
                        $${parseFloat(acc.balance).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </td>
                    <td><span class="badge ${statusClass}">${acc.status}</span></td>
                    <td>${actionsHtml}</td>
                </tr>
            `;
        }).join('');

    } catch (err) {
        showToast('Failed to load accounts: ' + err.message, 'error');
    }
}

async function handleCreateAccount(e) {
    e.preventDefault();

    const nameInput = document.getElementById('account-name');
    const typeInput = document.getElementById('account-type');
    const depositInput = document.getElementById('initial-deposit');
    const submitBtn = document.getElementById('btn-create-account');

    const name = nameInput.value.trim();
    const type = typeInput.value;
    const initialDeposit = parseFloat(depositInput.value) || 0;

    if (!name) {
        showToast('Please enter an account name', 'error');
        return;
    }

    try {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Creating...';

        await apiFetch('/accounts', {
            method: 'POST',
            body: JSON.stringify({
                accountName: name,
                accountType: type,
                initialDeposit: initialDeposit,
                currency: 'USD'
            })
        });

        showToast('Account created successfully!', 'success');
        nameInput.value = '';
        depositInput.value = '0.00';
        await loadAccounts();
    } catch (err) {
        showToast('Failed to create account: ' + err.message, 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Create Account';
    }
}

async function updateAccountStatus(accountId, newStatus) {
    try {
        await apiFetch(`/admin/accounts/${accountId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status: newStatus })
        });
        showToast(`Account status updated to ${newStatus}`, 'success');
        await loadAccounts();
    } catch (err) {
        showToast('Failed to update status: ' + err.message, 'error');
        await loadAccounts();
    }
}
