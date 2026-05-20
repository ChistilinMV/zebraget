const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = 3000;
const DB_PATH = path.join(__dirname, 'db.json');

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

// Ensure DB has minimum structure
let db = readDB();
if (!db.groups) db.groups = [];
if (!db.products) db.products = [];
writeDB(db);

// --- GROUPS API ---
app.get('/groups', (req, res) => {
    res.json(readDB().groups);
});

app.post('/groups', (req, res) => {
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

app.put('/groups/:id', (req, res) => {
    const db = readDB();
    const id = parseInt(req.params.id, 10);
    const index = db.groups.findIndex(g => g.id === id);
    if (index === -1) return res.status(404).json({ error: 'Group not found' });

    db.groups[index] = { ...db.groups[index], ...req.body, id };
    writeDB(db);
    res.json(db.groups[index]);
});

app.delete('/groups/:id', (req, res) => {
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
app.get('/products', (req, res) => {
    let products = readDB().products;
    if (req.query.groupId) {
        const groupId = parseInt(req.query.groupId, 10);
        products = products.filter(p => p.groupId === groupId);
    }
    res.json(products);
});

app.get('/products/:id', (req, res) => {
    const products = readDB().products;
    const id = parseInt(req.params.id, 10);
    const product = products.find(p => p.id === id);
    if (!product) return res.status(404).json({ error: 'Product not found' });
    res.json(product);
});

app.post('/products', (req, res) => {
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

app.patch('/products/:id', (req, res) => {
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

app.delete('/products/:id', (req, res) => {
    const db = readDB();
    const id = parseInt(req.params.id, 10);
    const index = db.products.findIndex(p => p.id === id);
    if (index === -1) return res.status(404).json({ error: 'Product not found' });

    db.products.splice(index, 1);
    writeDB(db);
    res.json({ success: true });
});

// --- IMPORT / EXPORT API ---
app.get('/api/export', (req, res) => {
    res.download(DB_PATH, 'db.json');
});

app.post('/api/import', (req, res) => {
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
