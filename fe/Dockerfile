# ---------------------------------------------------------------------------
# Stadio 1 - compilazione
#
# Qui servono Node e le dipendenze: sono ~500 MB che non hanno motivo di
# esistere in produzione, perche' quello che l'utente scarica sono solo file
# gia' pronti. Questo stadio viene buttato via alla fine.
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

# package.json e lock da soli, prima del resto del codice: finche' non cambiano,
# Docker riusa il layer delle dipendenze e non reinstalla niente. Copiare tutto
# insieme vorrebbe dire rifare `npm ci` a ogni virgola cambiata in un componente.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# ---------------------------------------------------------------------------
# Stadio 2 - immagine finale
#
# Solo nginx e i file prodotti. Niente Node, niente node_modules, niente
# sorgenti: quello che non c'e' non va aggiornato e non si puo' bucare.
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine

# La cartella `browser` e non tutta `dist`: accanto ci sono i sorgenti delle
# mappe e le licenze, che sul server non servono a nessuno.
COPY --from=build /app/dist/gestionale-frontend/browser /usr/share/nginx/html

# Non `conf.d` direttamente: l'immagine ufficiale passa i file in `templates`
# per envsubst all'avvio, cosi' l'indirizzo del backend si decide con una
# variabile d'ambiente invece che ricostruendo l'immagine.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

# Dove sta il backend. Si cambia all'avvio del container, senza ricostruire.
ENV API_UPSTREAM=http://backend:8080

# Fa scrivere all'entrypoint i DNS del container in NGINX_LOCAL_RESOLVERS, che
# il template usa nella direttiva `resolver`. Serve a far risolvere il nome del
# backend a ogni richiesta invece che una volta sola all'avvio: vedi il commento
# nel template.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

# envsubst tocca solo queste due: senza filtro sostituirebbe anche $uri, $host e
# le altre variabili di nginx, se per caso esistesse una variabile d'ambiente
# con lo stesso nome.
ENV NGINX_ENVSUBST_FILTER="^(API_|NGINX_LOCAL_RESOLVERS)"

EXPOSE 80
