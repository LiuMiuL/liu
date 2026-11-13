const C='ob15-2dfdb7b7';
const A=["./","index.html","manifest.webmanifest","icon-192.png","icon-512.png","img/home_dog.jpg","img/home_cat.jpg","img/street_dog.jpg","img/street_cat.jpg","img/feeder.jpg","img/newborn.jpg","img/petshop.jpg","img/mill.jpg"];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(A)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{
  if(e.request.method!=='GET')return;
  e.respondWith(fetch(e.request).then(r=>{const cp=r.clone();caches.open(C).then(c=>c.put(e.request,cp));return r})
    .catch(()=>caches.match(e.request).then(r=>r||caches.match('index.html'))));
});
