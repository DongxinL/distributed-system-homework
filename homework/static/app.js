(function () {
    function parseJsonSafely(text) {
        if (!text) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (error) {
            return null;
        }
    }

    async function request(url, options) {
        const response = await fetch(url, options);
        const text = await response.text();
        const data = parseJsonSafely(text) || text;
        if (!response.ok) {
            const message = typeof data === 'object' && data && data.message
                ? data.message
                : response.status + ' ' + response.statusText;
            throw new Error(message);
        }
        return data;
    }

    function getUser() {
        const raw = localStorage.getItem('user');
        return parseJsonSafely(raw);
    }

    function setUser(user) {
        localStorage.setItem('user', JSON.stringify(user));
    }

    function clearUser() {
        localStorage.removeItem('user');
    }

    function requireUser() {
        const user = getUser();
        if (!user) {
            window.location.href = 'login.html';
            return null;
        }
        return user;
    }

    function formatCurrency(value) {
        const amount = Number(value || 0);
        return new Intl.NumberFormat('zh-CN', {
            style: 'currency',
            currency: 'CNY',
            minimumFractionDigits: 2
        }).format(amount);
    }

    function formatDate(value) {
        if (!value) {
            return '-';
        }
        return new Date(value).toLocaleString('zh-CN', { hour12: false });
    }

    function setButtonLoading(button, loading, loadingText, normalText) {
        if (!button) {
            return;
        }
        button.disabled = loading;
        button.dataset.normalText = button.dataset.normalText || normalText || button.textContent;
        button.textContent = loading ? loadingText : (normalText || button.dataset.normalText);
    }

    function createToastContainer() {
        const container = document.createElement('div');
        container.className = 'toast-container';
        container.setAttribute('data-toast-container', 'true');
        document.body.appendChild(container);
        return container;
    }

    function showToast(message, type) {
        const container = document.querySelector('[data-toast-container]') || createToastContainer();
        const toast = document.createElement('div');
        toast.className = 'toast ' + (type || 'info');
        toast.textContent = message;
        container.appendChild(toast);
        window.setTimeout(function () {
            toast.remove();
        }, 2800);
    }

    function renderResultBox(target, data) {
        if (!target) {
            return;
        }
        if (typeof data === 'string') {
            target.textContent = data;
            return;
        }
        target.textContent = JSON.stringify(data, null, 2);
    }

    window.App = {
        request: request,
        getUser: getUser,
        setUser: setUser,
        clearUser: clearUser,
        requireUser: requireUser,
        formatCurrency: formatCurrency,
        formatDate: formatDate,
        setButtonLoading: setButtonLoading,
        showToast: showToast,
        renderResultBox: renderResultBox
    };
})();