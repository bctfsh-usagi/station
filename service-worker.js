const CACHE_NAME = 'next-station-v5-1';
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './assets/icons/icon-192.png',
  './assets/icons/icon-512.png',
  './assets/icons/apple-touch-icon.png',
  './assets/passengers/passenger_male_red.png',
  './assets/passengers/passenger_male_green.png',
  './assets/passengers/passenger_male_blue.png',
  './assets/passengers/passenger_male_yellow.png',
  './assets/passengers/passenger_male_purple.png',
  './assets/passengers/passenger_male_pink.png',
  './assets/passengers/passenger_female_red.png',
  './assets/passengers/passenger_female_green.png',
  './assets/passengers/passenger_female_blue.png',
  './assets/passengers/passenger_female_yellow.png',
  './assets/passengers/passenger_female_purple.png',
  './assets/passengers/passenger_female_pink.png'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  event.respondWith(caches.match(event.request).then(cached => cached || fetch(event.request)));
});
