import crypto from 'crypto';

const panelBaseUrl = (() => {
  const raw = process.env.XUI_PANEL_URL || process.env['3X_UI_URL'] || '';
  return String(raw).trim().replace(/\/+$/, '');
})();

const panelUsername = process.env.XUI_USERNAME || process.env['3X_UI_USERNAME'] || '';
const panelPassword = process.env.XUI_PASSWORD || process.env['3X_UI_PASSWORD'] || '';
const inboundId = Number.parseInt(process.env.XUI_INBOUND_ID || '1', 10);
const quotaGb = Number.parseInt(process.env.XUI_DEFAULT_QUOTA_GB || '10', 10);
const expiryDays = Number.parseInt(process.env.XUI_DEFAULT_EXPIRY_DAYS || '10', 10);
const subPath = process.env.XUI_SUB_PATH || '/sub/';
const subUriOverride = process.env.XUI_SUB_URI || '';

const ONE_GB_BYTES = 1024 * 1024 * 1024;

const normalizeSubPath = (pathValue) => {
  if (!pathValue) {
    return '/sub/';
  }
  let value = String(pathValue).trim();
  if (!value.startsWith('/')) {
    value = `/${value}`;
  }
  if (!value.endsWith('/')) {
    value = `${value}/`;
  }
  return value;
};

const ensureTrailingSlash = (urlValue) => {
  const trimmed = String(urlValue || '').trim();
  if (!trimmed) {
    return '';
  }
  return trimmed.endsWith('/') ? trimmed : `${trimmed}/`;
};

const randomToken = (length) => {
  const alphabet = 'abcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.randomBytes(length * 2);
  let out = '';
  for (let i = 0; i < bytes.length && out.length < length; i += 1) {
    out += alphabet[bytes[i] % alphabet.length];
  }
  return out;
};

const sanitizeEmailLabel = (value = '') => {
  const safe = String(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return safe || `user-${randomToken(6)}`;
};

const normalizeClientEmail = (user) => {
  const source = user?.name || user?.email || 'user';
  const safeLabel = sanitizeEmailLabel(source).slice(0, 20);
  const suffix = String(user?._id || '').slice(-6) || randomToken(6);
  return `${safeLabel}-${suffix}`;
};

class XuiApiClient {
  constructor() {
    this.cookieHeader = '';
  }

  isConfigured() {
    return Boolean(panelBaseUrl && panelUsername && panelPassword && Number.isInteger(inboundId));
  }

  buildUrl(path) {
    if (!path.startsWith('/')) {
      return `${panelBaseUrl}/${path}`;
    }
    return `${panelBaseUrl}${path}`;
  }

  captureCookies(response) {
    const getSetCookie = typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : [];

    const rawCookies = getSetCookie.length > 0
      ? getSetCookie
      : (() => {
        const single = response.headers.get('set-cookie');
        if (!single) {
          return [];
        }
        return single.split(/,(?=\s*[^;,\s]+=)/g);
      })();

    const compact = rawCookies
      .map((value) => String(value).split(';')[0].trim())
      .filter(Boolean);

    if (compact.length > 0) {
      this.cookieHeader = compact.join('; ');
    }
  }

  async request(path, { method = 'POST', body = null, authenticated = true } = {}) {
    const headers = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    };

    if (authenticated && this.cookieHeader) {
      headers.Cookie = this.cookieHeader;
    }

    const response = await fetch(this.buildUrl(path), {
      method,
      headers,
      body: body == null ? null : JSON.stringify(body),
    });

    this.captureCookies(response);

    const text = await response.text();
    let json = {};
    try {
      json = text ? JSON.parse(text) : {};
    } catch (error) {
      throw new Error(`3x-ui returned non-JSON response on ${path}`);
    }

    return { response, json };
  }

  async login() {
    const { json } = await this.request('/login', {
      authenticated: false,
      body: {
        username: panelUsername,
        password: panelPassword,
      },
    });

    if (!json.success) {
      throw new Error(json.msg || '3x-ui login failed');
    }
  }

  async getDefaultSettings() {
    const { json } = await this.request('/panel/setting/defaultSettings', { body: {} });
    if (!json.success) {
      return null;
    }
    return json.obj || null;
  }

  buildSubscriptionUrl(subId, defaultSettings) {
    const fromSettings = defaultSettings && typeof defaultSettings.subURI === 'string' ? defaultSettings.subURI : '';
    const base = ensureTrailingSlash(subUriOverride || fromSettings);
    if (base) {
      return `${base}${subId}`;
    }

    const parsed = new URL(panelBaseUrl);
    const normalizedPath = normalizeSubPath(subPath);
    return `${parsed.origin}${normalizedPath}${subId}`;
  }

  async createClientForUser(user) {
    await this.login();

    const now = Date.now();
    const totalBytes = Math.max(0, quotaGb) * ONE_GB_BYTES;
    const expiryMs = Math.max(0, expiryDays) === 0 ? 0 : now + (Math.max(0, expiryDays) * 24 * 60 * 60 * 1000);

    const client = {
      id: crypto.randomUUID(),
      flow: '',
      email: normalizeClientEmail(user),
      limitIp: 0,
      totalGB: totalBytes,
      expiryTime: expiryMs,
      enable: true,
      tgId: '',
      subId: randomToken(16),
      comment: user?.name || '',
      reset: 0,
    };

    const payload = {
      id: inboundId,
      settings: JSON.stringify({ clients: [client] }),
    };

    const { json } = await this.request('/panel/api/inbounds/addClient', { body: payload });
    if (!json.success) {
      throw new Error(json.msg || '3x-ui addClient failed');
    }

    const defaultSettings = await this.getDefaultSettings();

    return {
      inboundId,
      xuiClientEmail: client.email,
      xuiClientId: client.id,
      xuiSubId: client.subId,
      quotaBytes: totalBytes,
      expiryAt: expiryMs,
      subscriptionUrl: this.buildSubscriptionUrl(client.subId, defaultSettings),
    };
  }
}

export const isXuiConfigured = () => {
  const client = new XuiApiClient();
  return client.isConfigured();
};

export const provisionXuiClientForUser = async (user) => {
  const client = new XuiApiClient();
  if (!client.isConfigured()) {
    throw new Error('3x-ui is not configured. Set XUI_PANEL_URL, XUI_USERNAME, XUI_PASSWORD, XUI_INBOUND_ID.');
  }
  return client.createClientForUser(user);
};
