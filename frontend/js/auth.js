/**
 * LEDGERLINE - Authentication & Session Management
 */

const API_BASE = 'https://ledgerline-backend-5wnw.onrender.com/api';

const Auth = {
    getToken() {
        return localStorage.getItem('ledgerline_token');
    },

    getUser() {
        const userStr = localStorage.getItem('ledgerline_user');
        try {
            return userStr ? JSON.parse(userStr) : null;
        } catch (e) {
            return null;
        }
    },

    setSession(token, user) {
        localStorage.setItem('ledgerline_token', token);
        localStorage.setItem('ledgerline_user', JSON.stringify(user));
    },

    clearSession() {
        localStorage.removeItem('ledgerline_token');
        localStorage.removeItem('ledgerline_user');
    },

    isAuthenticated() {
        return !!this.getToken() && !!this.getUser();
    },

    hasRole(...roles) {
        const user = this.getUser();
        if (!user || !user.role) return false;
        return roles.includes(user.role);
    },

    requireAuth(allowedRoles = []) {
        if (!this.isAuthenticated()) {
            window.location.replace('login.html');
            return false;
        }

        if (allowedRoles.length > 0 && !this.hasRole(...allowedRoles)) {
            alert('Access Denied: You do not have permission to view this page.');
            window.location.href = 'dashboard.html';
            return false;
        }

        return true;
    },
        logout() {
        if (document.getElementById('logout-confirm-modal')) return;

        const modal = document.createElement('div');
        modal.id = 'logout-confirm-modal';
        modal.className = 'logout-modal-overlay';

        modal.innerHTML = `
            <div class="logout-modal">
                <div class="logout-modal-icon">🚪</div>
                <h2>Confirm Sign Out</h2>
                <p>Are you sure you want to sign out of your LedgerLine account?</p>
                <div class="logout-modal-actions">
                    <button type="button" id="cancel-logout"
                        class="logout-cancel-btn">Cancel</button>
                    <button type="button" id="confirm-logout"
                        class="logout-confirm-btn">Confirm Sign Out</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        modal.querySelector('#cancel-logout').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('#confirm-logout').addEventListener('click', () => {
            this.clearSession();
            sessionStorage.setItem('ledgerline_logged_out', 'true');
            window.location.replace('index.html');
        });

        modal.addEventListener('click', (event) => {
            if (event.target === modal) modal.remove();
        });
    },
    
};
window.Auth = Auth;

// Recheck authentication whenever a page is restored from browser history
window.addEventListener('pageshow', function () {
    const currentPage = window.location.pathname
        .split('/')
        .pop()
        .toLowerCase()
        .replace(/\.html$/, '');

    const publicPages = ['', 'index', 'login', 'register'];

    if (!publicPages.includes(currentPage) && !Auth.isAuthenticated()) {
        window.location.replace('login.html');
    }
});

/**
 * Global API Fetch Wrapper with Bearer Auth & Global Error Handling
 */
async function apiFetch(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
    
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    const token = Auth.getToken();
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(url, {
            ...options,
            headers
        });

        if (response.status === 401) {
            Auth.clearSession();
            showToast('Session expired. Please log in again.', 'error');
            setTimeout(() => {
               window.location.replace('login.html');
            }, 1200);
            throw new Error('Unauthorized');
        }

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            const errorMsg = data && (data.message || data.error) ? (data.message || data.error) : `Error ${response.status}: Request failed`;
            throw new Error(errorMsg);
        }

        return data;
    } catch (err) {
        if (err.message !== 'Unauthorized') {
            console.error(`API Fetch Error [${endpoint}]:`, err);
        }
        throw err;
    }
}

/**
 * Toast Notification Helper
 */
function showToast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    const icon = type === 'success' ? '✓' : (type === 'error' ? '✕' : 'ℹ');
    toast.innerHTML = `
        <div style="display:flex;align-items:center;gap:10px;">
            <span style="font-weight:700;">${icon}</span>
            <span>${message}</span>
        </div>
        <button style="background:none;border:none;color:#94a3b8;cursor:pointer;font-size:16px;" onclick="this.parentElement.remove()">×</button>
    `;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 4500);
}

/**
 * Dynamic Sidebar Navigation Renderer based on User Role
 */
function renderSidebar(activePage) {
    const sidebar = document.getElementById('sidebar-nav');
    if (!sidebar) return;

    const user = Auth.getUser();
    if (!user) return;

    let navHtml = `
        <div class="nav-section-title">Overview</div>
        <a href="dashboard.html" class="nav-item ${activePage === 'dashboard' ? 'active' : ''}">
            <span class="icon">📊</span> Dashboard
        </a>
    `;

    // Account Holder Options
    if (user.role === 'ROLE_ACCOUNT_HOLDER') {
        navHtml += `
            <div class="nav-section-title">Banking Operations</div>
            <a href="accounts.html" class="nav-item ${activePage === 'accounts' ? 'active' : ''}">
                <span class="icon">💳</span> My Accounts
            </a>
            <a href="deposit.html" class="nav-item ${activePage === 'deposit' ? 'active' : ''}">
                <span class="icon">📥</span> Deposit Funds
            </a>
            <a href="withdraw.html" class="nav-item ${activePage === 'withdraw' ? 'active' : ''}">
                <span class="icon">📤</span> Withdraw Funds
            </a>
            <a href="transfer.html" class="nav-item ${activePage === 'transfer' ? 'active' : ''}">
                <span class="icon">🔄</span> Transfer Money
            </a>
            <a href="notifications.html" class="nav-item ${activePage === 'notifications' ? 'active' : ''}">
                <span class="icon">🔔</span> Notifications
            </a>
            <div class="nav-section-title">Reports & History</div>
            <a href="transactions.html" class="nav-item ${activePage === 'transactions' ? 'active' : ''}">
                <span class="icon">📜</span> Transactions
            </a>
            <a href="statement.html" class="nav-item ${activePage === 'statement' ? 'active' : ''}">
                <span class="icon">📄</span> Account Statement
            </a>
        `;
    }

    // Auditor Options
    if (user.role === 'ROLE_AUDITOR') {
        navHtml += `
            <div class="nav-section-title">Compliance & Audit</div>
            <a href="accounts.html" class="nav-item ${activePage === 'accounts' ? 'active' : ''}">
                <span class="icon">🔍</span> System Accounts
            </a>
            <a href="transactions.html" class="nav-item ${activePage === 'transactions' ? 'active' : ''}">
                <span class="icon">📜</span> Ledger Entries
            </a>
            <a href="statement.html" class="nav-item ${activePage === 'statement' ? 'active' : ''}">
                <span class="icon">📄</span> Statements
            </a>
            <a href="audit.html" class="nav-item ${activePage === 'audit' ? 'active' : ''}">
                <span class="icon">🛡️</span> Security Audit Logs
            </a>
        `;
    }

    // Admin Options
    if (user.role === 'ROLE_ADMIN') {
        navHtml += `
            <div class="nav-section-title">Administration</div>
            <a href="accounts.html" class="nav-item ${activePage === 'accounts' ? 'active' : ''}">
                <span class="icon">💼</span> Account Management
            </a>
            <a href="transactions.html" class="nav-item ${activePage === 'transactions' ? 'active' : ''}">
                <span class="icon">📜</span> All Transactions
            </a>
            <a href="statement.html" class="nav-item ${activePage === 'statement' ? 'active' : ''}">
                <span class="icon">📄</span> Statements
            </a>
            <a href="audit.html" class="nav-item ${activePage === 'audit' ? 'active' : ''}">
                <span class="icon">🛡️</span> Audit Logs
            </a>
        `;
    }

    sidebar.innerHTML = navHtml;

    // Render User Badge in Footer
    const userBadgeName = document.getElementById('user-display-name');
    const userBadgeRole = document.getElementById('user-display-role');
    const userAvatar = document.getElementById('user-display-avatar');

    if (userBadgeName) userBadgeName.textContent = user.fullName || user.username;
    if (userBadgeRole) {
        const roleLabels = {
            'ROLE_ADMIN': 'Administrator',
            'ROLE_ACCOUNT_HOLDER': 'Account Holder',
            'ROLE_AUDITOR': 'Compliance Auditor'
        };
        userBadgeRole.textContent = roleLabels[user.role] || user.role;
    }
    if (userAvatar) {
        userAvatar.textContent = (user.fullName || user.username || 'U').charAt(0).toUpperCase();
    }
}

// Global modal helper
function showConfirmModal(title, message, detailsHtml, onConfirm) {
    let overlay = document.getElementById('confirm-modal-overlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'confirm-modal-overlay';
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="modal-card">
                <div class="modal-header">
                    <div class="modal-icon" style="background:#eef2ff;color:#4f46e5;">⚠️</div>
                    <h3 class="modal-title" id="modal-title-text">Confirm Operation</h3>
                </div>
                <div class="modal-body" id="modal-body-text"></div>
                <div class="modal-details" id="modal-details-container"></div>
                <div class="modal-actions">
                    <button class="btn btn-outline btn-sm" id="modal-btn-cancel">Cancel</button>
                    <button class="btn btn-primary btn-sm" id="modal-btn-confirm">Confirm & Execute</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);
    }

    document.getElementById('modal-title-text').textContent = title;
    document.getElementById('modal-body-text').textContent = message;
    
    const detailsContainer = document.getElementById('modal-details-container');
    if (detailsHtml) {
        detailsContainer.style.display = 'block';
        detailsContainer.innerHTML = detailsHtml;
    } else {
        detailsContainer.style.display = 'none';
    }

    const cancelBtn = document.getElementById('modal-btn-cancel');
    const confirmBtn = document.getElementById('modal-btn-confirm');

    const closeModal = () => {
        overlay.classList.remove('active');
    };

    cancelBtn.onclick = closeModal;
    confirmBtn.onclick = () => {
        closeModal();
        if (typeof onConfirm === 'function') {
            onConfirm();
        }
    };

    overlay.classList.add('active');
}

/**
 * Login Page Handler
 */
function initLoginPage() {

    if (Auth.isAuthenticated()) {
        window.location.href = 'dashboard.html';
        return;
    }

    const form = document.getElementById('form-login');
    if (!form) return;

    let selectedRole = null;

    const roleButtons = document.querySelectorAll('.role-option');
    const roleDescription = document.getElementById('role-description');
    const identifierInput = document.getElementById('login-identifier');

    const roleDetails = {
        ROLE_ACCOUNT_HOLDER: {
            label: 'Account Holder',
            placeholder: 'Enter account holder username',
            description: 'Manage accounts, balances and transactions.'
        },
        ROLE_ADMIN: {
            label: 'Administrator',
            placeholder: 'Enter administrator username',
            description: 'Manage users and oversee banking operations.'
        },
        ROLE_AUDITOR: {
            label: 'Auditor',
            placeholder: 'Enter auditor username',
            description: 'Review ledger entries and security audit records.'
        }
    };

    roleButtons.forEach(button => {
            button.addEventListener('click', () => {
        
                selectedRole = button.dataset.role;
        
                roleButtons.forEach(btn => {
                    btn.classList.remove('active');
                });
        
                button.classList.add('active');
        
                const details = roleDetails[selectedRole];
        
                identifierInput.placeholder = details.placeholder;
                roleDescription.textContent = details.description;
        
                // Fill demo credentials automatically
                const demoCredentials = {
                    ROLE_ACCOUNT_HOLDER: {
                        username: 'john_doe',
                        password: 'User@12345'
                    },
                    ROLE_ADMIN: {
                        username: 'admin',
                        password: 'Admin@12345'
                    },
                    ROLE_AUDITOR: {
                        username: 'auditor_jane',
                        password: 'Auditor@12345'
                    }
                };
        
                const credentials = demoCredentials[selectedRole];
        
                if (credentials) {
                    identifierInput.value = credentials.username;
                    document.getElementById('login-password').value =
                        credentials.password;
        
                    roleDescription.textContent =
                        details.description + ' Demo credentials filled!';
                }
            });

    });
    // Click demo credentials to fill login fields
    document.querySelectorAll('.demo-credential').forEach(card => {
    
        card.addEventListener('click', () => {
    
            selectedRole = card.dataset.role;
    
            identifierInput.value = card.dataset.username;
    
            document.getElementById('login-password').value =
                card.dataset.password;
    
            roleButtons.forEach(btn => {
                btn.classList.toggle(
                    'active',
                    btn.dataset.role === selectedRole
                );
            });
    
            const details = roleDetails[selectedRole];
    
            if (details) {
                identifierInput.placeholder = details.placeholder;
                roleDescription.textContent = details.description;
            }
    
            showToast('Demo credentials filled! Click Sign In.', 'success');
        });
    });
    form.addEventListener('submit', async (e) => {

        e.preventDefault();

        const identifierInput = document.getElementById('login-identifier');
        const passwordInput = document.getElementById('login-password');
        const submitBtn = document.getElementById('btn-login-submit');

        const usernameOrEmail = identifierInput.value.trim();
        const password = passwordInput.value;

        if (!selectedRole) {
            showToast('Please select your login role first.', 'error');
            return;
        }

        if (!usernameOrEmail || !password) {
            showToast('Please enter your username/email and password.', 'error');
            return;
        }

        try {

            submitBtn.disabled = true;
            submitBtn.textContent = 'Authenticating...';

            const res = await apiFetch('/auth/login', {
                method: 'POST',
                body: JSON.stringify({
                    usernameOrEmail,
                    password
                })
            });

            const data = res.data;

            // Verify selected role against the actual backend role
            if (data.role !== selectedRole) {
                showToast(
                    'Selected role does not match this account. Please choose the correct role.',
                    'error'
                );
                return;
            }

            Auth.setSession(data.token, {
                userId: data.userId,
                username: data.username,
                email: data.email,
                fullName: data.fullName,
                role: data.role
            });

            showToast(
                `Welcome back, ${data.fullName || data.username}!`,
                'success'
            );

            setTimeout(() => {
                window.location.replace('dashboard.html');
            }, 600);

        } catch (err) {

            showToast('Authentication failed: ' + err.message, 'error');

        } finally {

            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In';

        }

    });
}
/**
 * Registration Page Handler
 */
function initRegisterPage() {
    if (Auth.isAuthenticated()) {
        window.location.href = 'dashboard.html';
        return;
    }

    const form = document.getElementById('form-register');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fullName = document.getElementById('reg-fullname').value.trim();
        const username = document.getElementById('reg-username').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const password = document.getElementById('reg-password').value;
        const confirmPassword = document.getElementById('reg-confirm-password').value;
        const submitBtn = document.getElementById('btn-register-submit');

        if (!fullName || !username || !email || !password) {
            showToast('Please fill out all required fields', 'error');
            return;
        }

        if (password !== confirmPassword) {
            showToast('Passwords do not match', 'error');
            return;
        }

        if (password.length < 8) {
            showToast('Password must be at least 8 characters long', 'error');
            return;
        }

        try {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Creating Account...';

            const res = await apiFetch('/auth/register', {
                method: 'POST',
                body: JSON.stringify({ fullName, username, email, password })
            });

            showToast(
                'Registration successful! Your account is waiting for admin approval.',
                'success'
            );

            setTimeout(() => {
                window.location.replace('login.html');
            }, 2500);

        } catch (err) {
            showToast('Registration failed: ' + err.message, 'error');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Create Account';
        }
    });
}

// Quick Login Demo Helpers
function fillDemoCredentials(identifier, password) {
    const idInput = document.getElementById('login-identifier');
    const pwdInput = document.getElementById('login-password');
    if (idInput && pwdInput) {
        idInput.value = identifier;
        pwdInput.value = password;
        showToast(`Filled credentials for ${identifier}`, 'info');
    }
}

// Global page initialization
document.addEventListener('DOMContentLoaded', () => {
    const pathname = window.location.pathname;
    const page = pathname.substring(pathname.lastIndexOf('/') + 1).replace('.html', '');

    if (page === 'login') {
        initLoginPage();
    } else if (page === 'register') {
        initRegisterPage();
    }
});
