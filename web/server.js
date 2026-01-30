const jsonServer = require('json-server');
const server = jsonServer.create();
const router = jsonServer.router('db.json');
const middlewares = jsonServer.defaults();

// Set default middlewares (logger, static, cors and no-cache)
server.use(middlewares);

// Add custom logic if needed, but defaults serve ./public
// For example, redirect root to admin.html if desired, or just access /admin.html directly.

server.use(router);

const PORT = 3000;
server.listen(PORT, () => {
  console.log('JSON Server is running');
  console.log(`API at http://localhost:${PORT}/products`);
  console.log(`Admin at http://localhost:${PORT}/admin.html`);
});
