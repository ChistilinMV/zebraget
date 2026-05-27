const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = 3000;
const DB_PATH = path.join(__dirname, 'db.json');
const USERS_DB_PATH = path.join(__dirname, 'users.json');

app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.static('public', {
    setHeaders: (res, path) => {
        if (path.endsWith('.html')) {
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
        }
    }
}));

app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    next();
});

// DB Helper functions
function readDB() {
    try {
        const data = fs.readFileSync(DB_PATH, 'utf8');
        return JSON.parse(data);
    } catch (err) {
        console.error('Error reading DB:', err);
        return { groups: [], products: [] };
    }
}

function writeDB(data) {
    fs.writeFileSync(DB_PATH, JSON.stringify(data, null, 2), 'utf8');
}

function readUsersDB() {
    try {
        if (!fs.existsSync(USERS_DB_PATH)) return { users: [], sessions: [] };
        const data = fs.readFileSync(USERS_DB_PATH, 'utf8');
        return JSON.parse(data);
    } catch (err) {
        console.error('Error reading users DB:', err);
        return { users: [], sessions: [] };
    }
}

function writeUsersDB(data) {
    fs.writeFileSync(USERS_DB_PATH, JSON.stringify(data, null, 2), 'utf8');
}

// Ensure DB has minimum structure
let db = readDB();
if (!db.groups) db.groups = [];
if (!db.products) db.products = [];
writeDB(db);

let usersDb = readUsersDB();
if (!usersDb.users) usersDb.users = [];
if (!usersDb.sessions) usersDb.sessions = [];
writeUsersDB(usersDb);

// --- AUTHENTICATION ---
const activeTokens = new Set();
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Mva5n$M9';

app.post('/api/login', (req, res) => {
    const { password } = req.body;
    if (password === ADMIN_PASSWORD) {
        const token = crypto.randomBytes(32).toString('hex');
        activeTokens.add(token);
        res.json({ success: true, token });
    } else {
        res.status(401).json({ error: 'Неверный пароль' });
    }
});

const requireAuth = (req, res, next) => {
    let token;
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.split(' ')[1];
    } else if (req.query.token) {
        token = req.query.token;
    }

    if (!token || !activeTokens.has(token)) {
        return res.status(401).json({ error: 'Не авторизован как администратор' });
    }
    next();
};

const requireClientOrAdminAuth = (req, res, next) => {
    let token;
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.split(' ')[1];
    } else if (req.query.token) {
        token = req.query.token;
    }

    if (!token) return res.status(401).json({ error: 'Не авторизован' });

    // Is it admin?
    if (activeTokens.has(token)) {
        return next();
    }

    // Is it client?
    const uDb = readUsersDB();
    const session = uDb.sessions.find(s => s.token === token);
    if (!session) {
        return res.status(401).json({ error: 'Неверный или отозванный токен' });
    }

    // Attach user context
    req.clientSession = session;
    next();
};

// --- CLIENT AUTH API ---
app.post('/api/client/login', (req, res) => {
    const { username, password, deviceId } = req.body;
    if (!username || !password || !deviceId) {
        return res.status(400).json({ error: 'Требуется username, password и deviceId' });
    }

    const uDb = readUsersDB();
    const user = uDb.users.find(u => u.username === username && u.password === password);
    if (!user) {
        return res.status(401).json({ error: 'Неверные учетные данные' });
    }

    const maxDevices = user.maxDevices || 2;
    const userSessions = uDb.sessions.filter(s => s.username === username);
    
    // Check if device already has a session
    let existingSessionIndex = userSessions.findIndex(s => s.deviceId === deviceId);
    
    if (existingSessionIndex === -1 && userSessions.length >= maxDevices) {
        return res.status(403).json({ error: 'Лимит устройств исчерпан' });
    }

    const token = crypto.randomBytes(32).toString('hex');
    const newSession = { token, username, deviceId, createdAt: Date.now() };

    if (existingSessionIndex !== -1) {
        // Update existing session for this device
        const globalIndex = uDb.sessions.findIndex(s => s.deviceId === deviceId && s.username === username);
        uDb.sessions[globalIndex] = newSession;
    } else {
        uDb.sessions.push(newSession);
    }

    writeUsersDB(uDb);
    res.json({ token });
});

app.post('/api/client/logout', requireClientOrAdminAuth, (req, res) => {
    if (req.clientSession) {
        const uDb = readUsersDB();
        uDb.sessions = uDb.sessions.filter(s => s.token !== req.clientSession.token);
        writeUsersDB(uDb);
    }
    res.json({ success: true });
});

// --- ADMIN USERS API ---
app.get('/api/users', requireAuth, (req, res) => {
    const uDb = readUsersDB();
    // omit passwords and attach session counts
    const result = uDb.users.map(u => {
        const activeDevices = uDb.sessions.filter(s => s.username === u.username).length;
        const sessions = uDb.sessions.filter(s => s.username === u.username);
        return { id: u.id, username: u.username, maxDevices: u.maxDevices, activeDevices, sessions };
    });
    res.json(result);
});

app.post('/api/users', requireAuth, (req, res) => {
    const uDb = readUsersDB();
    if (uDb.users.some(u => u.username === req.body.username)) {
        return res.status(400).json({ error: 'Пользователь уже существует' });
    }
    const newUser = {
        id: Date.now(),
        username: req.body.username,
        password: req.body.password,
        maxDevices: parseInt(req.body.maxDevices) || 2
    };
    uDb.users.push(newUser);
    writeUsersDB(uDb);
    res.status(201).json({ id: newUser.id, username: newUser.username });
});

app.put('/api/users/:id', requireAuth, (req, res) => {
    const uDb = readUsersDB();
    const id = parseInt(req.params.id, 10);
    const index = uDb.users.findIndex(u => u.id === id);
    if (index === -1) return res.status(404).json({ error: 'User not found' });

    // Update password only if provided
    if (req.body.password) {
        uDb.users[index].password = req.body.password;
    }
    if (req.body.maxDevices) {
        uDb.users[index].maxDevices = parseInt(req.body.maxDevices);
    }
    
    writeUsersDB(uDb);
    res.json({ success: true });
});

app.delete('/api/users/:id', requireAuth, (req, res) => {
    const uDb = readUsersDB();
    const id = parseInt(req.params.id, 10);
    const user = uDb.users.find(u => u.id === id);
    if (!user) return res.status(404).json({ error: 'User not found' });

    uDb.users = uDb.users.filter(u => u.id !== id);
    // remove sessions
    uDb.sessions = uDb.sessions.filter(s => s.username !== user.username);
    
    writeUsersDB(uDb);
    res.json({ success: true });
});

app.delete('/api/users/:id/sessions/:deviceId', requireAuth, (req, res) => {
    const uDb = readUsersDB();
    const id = parseInt(req.params.id, 10);
    const user = uDb.users.find(u => u.id === id);
    if (!user) return res.status(404).json({ error: 'User not found' });

    uDb.sessions = uDb.sessions.filter(s => !(s.username === user.username && s.deviceId === req.params.deviceId));
    
    writeUsersDB(uDb);
    res.json({ success: true });
});

// --- GROUPS API ---
app.get('/groups', requireClientOrAdminAuth, (req, res) => {
    res.json(readDB().groups);
});

app.post('/groups', requireAuth, (req, res) => {
    const db = readDB();
    const newGroup = {
        id: Date.now(), // simple ID generation
        name: req.body.name,
        imageUrl: req.body.imageUrl
    };
    db.groups.push(newGroup);
    writeDB(db);
    res.status(201).json(newGroup);
});

app.put('/groups/:id', requireAuth, (req, res) => {
    const db = readDB();
    const id = parseInt(req.params.id, 10);
    const index = db.groups.findIndex(g => g.id === id);
    if (index === -1) return res.status(404).json({ error: 'Group not found' });

    db.groups[index] = { ...db.groups[index], ...req.body, id };
    writeDB(db);
    res.json(db.groups[index]);
});

app.delete('/groups/:id', requireAuth, (req, res) => {
    const db = readDB();
    const id = parseInt(req.params.id, 10);
    
    // Check if group is empty
    const hasProducts = db.products.some(p => p.groupId === id);
    if (hasProducts) {
        return res.status(400).json({ error: 'Cannot delete group because it contains products' });
    }

    const index = db.groups.findIndex(g => g.id === id);
    if (index === -1) return res.status(404).json({ error: 'Group not found' });

    db.groups.splice(index, 1);
    writeDB(db);
    res.json({ success: true });
});

// --- PRODUCTS API ---
app.get('/products', requireClientOrAdminAuth, (req, res) => {
    let products = readDB().products;
    if (req.query.groupId) {
        const groupId = parseInt(req.query.groupId, 10);
        products = products.filter(p => p.groupId === groupId);
    }
    res.json(products);
});

app.get('/products/:id', requireClientOrAdminAuth, (req, res) => {
    const products = readDB().products;
    const id = parseInt(req.params.id, 10);
    const product = products.find(p => p.id === id);
    if (!product) return res.status(404).json({ error: 'Product not found' });
    res.json(product);
});

app.post('/products', requireAuth, (req, res) => {
    const db = readDB();
    const newProduct = {
        id: Date.now(),
        name: req.body.name,
        imageUrl: req.body.imageUrl,
        barcodeValue: req.body.barcodeValue,
        barcodeFormat: req.body.barcodeFormat || 'EAN_13',
        groupId: req.body.groupId ? parseInt(req.body.groupId, 10) : null
    };
    db.products.push(newProduct);
    writeDB(db);
    res.status(201).json(newProduct);
});

app.patch('/products/:id', requireAuth, (req, res) => {
    const db = readDB();
    const id = parseInt(req.params.id, 10);
    const index = db.products.findIndex(p => p.id === id);
    if (index === -1) return res.status(404).json({ error: 'Product not found' });

    if (req.body.groupId) {
        req.body.groupId = parseInt(req.body.groupId, 10);
    } else if (req.body.groupId === null || req.body.groupId === "") {
        req.body.groupId = null;
    }

    db.products[index] = { ...db.products[index], ...req.body, id };
    writeDB(db);
    res.json(db.products[index]);
});

app.delete('/products/:id', requireAuth, (req, res) => {
    const db = readDB();
    const id = parseInt(req.params.id, 10);
    const index = db.products.findIndex(p => p.id === id);
    if (index === -1) return res.status(404).json({ error: 'Product not found' });

    db.products.splice(index, 1);
    writeDB(db);
    res.json({ success: true });
});

// --- IMPORT / EXPORT API ---
app.get('/api/export', requireAuth, (req, res) => {
    res.download(DB_PATH, 'db.json');
});

app.post('/api/import', requireAuth, (req, res) => {
    if (!req.body || typeof req.body !== 'object') {
        return res.status(400).json({ error: 'Invalid JSON body' });
    }
    const { groups, products } = req.body;
    if (!Array.isArray(groups) || !Array.isArray(products)) {
        return res.status(400).json({ error: 'JSON must contain groups and products arrays' });
    }
    
    writeDB({ groups, products });
    res.json({ success: true });
});

app.listen(PORT, () => {
    console.log(`Express server running on port ${PORT}`);
    console.log(`API at http://localhost:${PORT}/products and /groups`);
    console.log(`Admin at http://localhost:${PORT}/admin.html`);
});
