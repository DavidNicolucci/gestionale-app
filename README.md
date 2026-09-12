# Gestionale

Applicazione gestionale full-stack per la gestione di **clienti, siti, dipendenti e ore lavorate (timesheet)**,
con importazione massiva delle ore da file Excel e un assistente AI in grado di interrogare i dati.

Il progetto è un **monorepo** che contiene backend, frontend e l'infrastruttura di sviluppo (Docker Compose):
non è un sistema a microservizi, ma un **monolite modulare** — scelta voluta, trattandosi di un'applicazione
sviluppata e usata in locale da un singolo sviluppatore.

---

## Indice

- [Cosa fa](#cosa-fa)
- [Stack tecnologico](#stack-tecnologico)
- [Architettura](#architettura)
- [Struttura del repository](#struttura-del-repository)
- [Prerequisiti](#prerequisiti)
- [Avvio da zero](#avvio-da-zero)
- [Uso quotidiano](#uso-quotidiano)
- [Porte e indirizzi](#porte-e-indirizzi)
- [API](#api)
- [Modello dati](#modello-dati)
- [Convenzioni di sviluppo](#convenzioni-di-sviluppo)
- [Stato del progetto](#stato-del-progetto)

---

## Cosa fa

| Area | Descrizione |
|---|---|
| **Anagrafica clienti** | CRUD completo, ricerca paginata con filtri. L'eliminazione di un cliente rimuove in cascata i suoi siti. |
| **Anagrafica siti** | Ogni sito appartiene a un cliente. Cancellazione fisica, bloccata (409) se esistono ore registrate sul sito. |
| **Anagrafica dipendenti** | CRUD + **ciclo di vita del contratto**: stato derivato `ATTIVO` / `IN_SCADENZA` / `SCADUTO` / `ELIMINATO`, cancellazione **logica**, endpoint dedicati di *ripristino* e *rinnovo*, avviso sui contratti in scadenza (finestra di 15 giorni). |
| **Timesheet** | Registrazione delle ore per dipendente/sito/giorno. Le ore si validano contro la data di lavoro, non contro la data odierna. |
| **Import Excel asincrono** | Upload di un file Excel → il percorso viene pubblicato su RabbitMQ → un consumer lo legge con Apache POI e crea i timesheet. La risposta è `202 Accepted`; i messaggi in errore finiscono in una **DLQ** invece di bloccare il connettore. |
| **Assistente AI** | Chat con Google Gemini (`gemini-2.5-flash`) e *function calling*: l'assistente interroga davvero clienti, siti, dipendenti e timesheet tramite tool dedicati. Lo storico è persistito per utente. |
| **Autenticazione e ruoli** | Login con JWT in **cookie HttpOnly**, RBAC a due ruoli (`ADMIN`, `OPERATOR`): lettura aperta a entrambi, scrittura e cancellazione solo `ADMIN`. |
| **Observability** | Trace su Jaeger, metriche su Prometheus, dashboard Grafana, health check su interfaccia di management separata. |

---

## Stack tecnologico

**Backend**
- Java 21, **Quarkus 3.37**
- Hibernate ORM con **Panache** (pattern repository), Hibernate Validator
- Microsoft SQL Server 2022 (JDBC mssql)
- SmallRye Reactive Messaging + **RabbitMQ 3.13**
- Apache POI 5.5.1 (lettura Excel)
- SmallRye JWT + security-jpa (Bcrypt), SmallRye OpenAPI (Swagger UI)
- Quarkus Cache (Caffeine)
- LangChain4j + Google Gemini (`quarkus-langchain4j-ai-gemini`)
- OpenTelemetry, Micrometer/Prometheus, SmallRye Health

**Frontend**
- **Angular 22** standalone, **signals**, **zoneless**, SCSS, routing lazy
- **Angular Material** + CDK (tema Azure/Blue)
- `class-transformer` (`plainToInstance`) per mappare le risposte HTTP in istanze di classe
- Proxy di sviluppo `/api/*` → backend HTTPS

**Infrastruttura di sviluppo**
- Docker Compose: SQL Server, RabbitMQ, Jaeger, Prometheus, Grafana + un container usa-e-getta per lo schema DB
- HTTPS con certificato self-signed generato in locale

---

## Architettura

```
Browser  ──►  Angular 22 (dev server :4200)
                   │  proxy /api/* (same-origin → il cookie JWT viaggia da solo)
                   ▼
              Quarkus (https://localhost:8443)
                   │
      Resource ──► Service ──► Repository (Panache)
        (REST)      (logica)      (dati)
                   │                 │
                   │                 ▼
                   │            SQL Server :1433
                   │
                   ├──► RabbitMQ :5672 ──► ImportConsumer (Apache POI) ──► timesheet
                   │
                   └──► Google Gemini (tool: clienti, siti, dipendenti, timesheet)

  Management :9000 (health, metrics) ──► Prometheus ──► Grafana
  OTLP :4317 ──────────────────────────► Jaeger
```

**Tre layer, sempre in quest'ordine**: `Resource` (REST, validazione, ruoli) → `Service` (logica di dominio,
transazioni, cache) → `Repository` (accesso ai dati). I DTO `Request` / `PatchRequest` / `Response` sono
separati dalle entity e convertiti da mapper statici; le entity non escono mai dal service.

**Scelte architetturali di fondo** (già prese, non da rimettere in discussione):

1. **Monolite modulare**, non microservizi.
2. **Schema DB gestito a mano** in `docker/sqlserver/init/01-schema.sql`, con
   `quarkus.hibernate-orm.database.generation=none`. Hibernate non tocca lo schema: ogni nuova entity richiede
   di aggiungere la tabella nello script SQL a mano.
3. **JWT in cookie HttpOnly**, non in `localStorage` (un XSS non può leggerlo). Conseguenza: **niente HTTP
   Interceptor** lato Angular, il browser invia il cookie da solo; il frontend, che il cookie non può leggerlo,
   chiama `GET /api/auth/me` all'avvio per sapere se la sessione è valida. L'header `Authorization: Bearer`
   **non è più accettato** e la Basic Auth è disattivata.
4. **Ricerca paginata via `POST /{risorsa}/ricerca`**, non `GET` con query string: i filtri sono un oggetto
   (`RicercaPaginataRequest` + `XxxRicercaRequest`) e la risposta è `PaginaResponse<T>`. Il `GET /{risorsa}`
   semplice resta per gli elenchi non filtrati (per esempio per riempire una tendina).
5. **Cancellazione logica solo sui dipendenti**: si persiste la decisione umana (`eliminato`), non ciò che è
   derivabile (`SCADUTO` si calcola da `dataScadenza`, non si salva in colonna). Clienti e siti restano a
   cancellazione fisica, per scelta.

---

## Struttura del repository

```
gestionale-app/
├── be/                                  backend Quarkus (com.gestionale)
│   ├── src/main/java/com/gestionale/dominio/
│   │   ├── ai/            servizio chat + tools di function calling
│   │   ├── auth/          login/logout/me, emissione JWT
│   │   ├── handler/       exception mapper centralizzati (400/404/409/500)
│   │   ├── imports/       upload, producer e consumer RabbitMQ dell'import Excel
│   │   ├── model/         entity, dto, enums
│   │   ├── observability/ health check e metriche custom
│   │   ├── repository/    PanacheRepository + filtri
│   │   ├── resources/     endpoint REST (Cliente, Dipendente, Sito, Timesheet)
│   │   ├── security/      utenti, ruoli, gestione password
│   │   └── service/       logica di dominio
│   ├── genera-keystore.ps1              rigenera il certificato self-signed per l'HTTPS
│   └── .env                             GEMINI_API_KEY, DB_PASSWORD, RABBITMQ_*, KEYSTORE_PASSWORD (git-ignored)
│
├── fe/                                  frontend Angular
│   └── src/app/
│       ├── components/layout-autenticato/   guscio con guard unico + assistente AI
│       ├── sections/{home,login,clienti,dipendenti,siti,timesheet}/
│       │   ├── components/  presentazionali (input()/output())
│       │   ├── constants/   testi, messaggi d'errore, conferme
│       │   ├── enums/ interfaces/
│       │   ├── pages/       collante fra stato e componenti
│       │   └── services/    <nome>-api.service.ts (HTTP) + <nome>-query.service.ts (stato con signals)
│       ├── services/{auth,assistente}/
│       └── shared/          ConfermaDialog/Service, GoBack, paginatore italiano, model, enum, validator
│
├── docker/
│   ├── sqlserver/init/01-schema.sql      schema del database (fonte di verità)
│   ├── prometheus/prometheus.yml
│   └── grafana/provisioning/{datasources,dashboards}
├── docker-compose.yml
└── .env                                  segreti letti da docker compose (git-ignored)
```

---

## Prerequisiti

- **Docker Desktop** (SQL Server, RabbitMQ, Jaeger, Prometheus, Grafana)
- **JDK 21** (il backend si avvia con il wrapper `mvnw`, Maven non serve installato)
- **Node 22** + npm (consigliato via `nvm`)
- Una **chiave API di Google AI Studio** per l'assistente AI ([aistudio.google.com/apikey](https://aistudio.google.com/apikey)), free tier sufficiente
- Su Windows: PowerShell per gli script, Git Bash dove serve OpenSSL

---

## Avvio da zero

### 1. File dei segreti

Entrambi i file `.env` sono **git-ignored** e vanno creati a mano.

`gestionale-app/.env` — letto da Docker Compose:

```dotenv
DB_PASSWORD=<password dell'utente sa di SQL Server>
RABBITMQ_USER=<utente del broker>
RABBITMQ_PASSWORD=<password del broker>
```

`be/.env` — letto da Quarkus:

```dotenv
GEMINI_API_KEY=<chiave di Google AI Studio>
DB_PASSWORD=<la stessa del file sopra>
RABBITMQ_USER=<lo stesso del file sopra>
RABBITMQ_PASSWORD=<la stessa del file sopra>
KEYSTORE_PASSWORD=<password del keystore TLS>
```

> ⚠️ **Lancia `docker compose` dalla cartella `gestionale-app/`, mai da `be/`.** Compose legge il `.env`
> che sta accanto al `docker-compose.yml`: partendo da un'altra cartella `RABBITMQ_USER` non viene
> valorizzato, il broker resta con il solo utente `guest` e l'applicazione riceve `ACCESS_REFUSED`.

### 2. Infrastruttura

```powershell
docker compose up -d
```

Il container `sqlserver-init` è **usa-e-getta**: parte una volta sola, applica `01-schema.sql` e termina.

### 3. Certificato HTTPS

```powershell
cd be
.\genera-keystore.ps1 -Password "<la stessa KEYSTORE_PASSWORD>"
```

Genera `be/src/main/resources/server-keystore.p12` (self-signed, valido per `localhost`), anch'esso git-ignored.

### 4. Chiavi RSA per il JWT

`be/src/main/resources/privateKey.pem` e `publicKey.pem` (git-ignored) servono a firmare e verificare i token.
Da Git Bash:

```bash
openssl genrsa -out be/src/main/resources/privateKey.pem 2048
openssl rsa -in be/src/main/resources/privateKey.pem -pubout -out be/src/main/resources/publicKey.pem
```

### 5. Backend

```powershell
cd be
.\mvnw compile quarkus:dev
```

### 6. Frontend

```powershell
cd fe
npm install
npm start
```

Applicazione su <http://localhost:4200>. Utente di bootstrap: **`admin` / `admin123`**.

---

## Uso quotidiano

```powershell
docker compose start          # i container NON ripartono da soli dopo un riavvio del PC
cd be; .\mvnw compile quarkus:dev
cd fe; npm start
```

> ⚠️ **Non usare `docker compose down -v`**: cancella i volumi, cioè tutti i dati.

**Comandi utili**

| Comando | Cosa fa |
|---|---|
| `docker compose up sqlserver-init` | rilancia lo script dello schema dopo una modifica a `01-schema.sql` |
| `cd be; .\mvnw compile` | compila il backend |
| `cd fe; npm run build` | build di produzione del frontend |
| `cd fe; npx prettier --write src` | formatta il codice frontend |

> ⚠️ **Le modifiche allo schema non arrivano da sole sul database.** Se lo stack è già in esecuzione,
> `sqlserver-init` non gira: aggiungere una colonna e riavviare Quarkus non basta, le chiamate rispondono
> `500 Invalid column name` con codice che compila perfettamente. Va rilanciato il container di init, oppure
> — se il bind mount di Docker Desktop non regge — lo script va copiato dentro il container con `docker cp`
> ed eseguito con `sqlcmd -i` (da Git Bash serve `MSYS_NO_PATHCONV=1`, altrimenti i path `/opt/...` vengono
> convertiti in path Windows).

---

## Porte e indirizzi

| Servizio | Indirizzo |
|---|---|
| Frontend Angular (dev) | <http://localhost:4200> |
| Backend Quarkus | <https://localhost:8443> (la 8080 redirige a HTTPS) |
| Swagger UI | <https://localhost:8443/q/swagger-ui/> |
| Health / metriche (management) | <http://localhost:9000/q/health> · `/q/metrics` |
| SQL Server | `localhost:1433` |
| RabbitMQ (AMQP · UI) | `localhost:5672` · <http://localhost:15672> |
| Jaeger UI | <http://localhost:16686> |
| Prometheus | <http://localhost:9090> |
| Grafana | <http://localhost:3000> |

L'interfaccia di management sta su una porta separata di proposito: così Prometheus non deve seguire il
redirect HTTPS né ignorare il certificato self-signed.

---

## API

Tutte le rotte stanno sotto `/api`. Salvo diversa indicazione: **lettura** `ADMIN` + `OPERATOR`,
**scrittura ed eliminazione** solo `ADMIN`.

| Metodo | Rotta | Note |
|---|---|---|
| `POST` | `/api/auth/login` | pubblico; imposta il cookie `gestionale_jwt` (HttpOnly, Secure, SameSite=Strict). La sessione si rinnova a ogni chiamata: scade dopo 30 min di inattività, e comunque 12 h dopo l'accesso |
| `POST` | `/api/auth/logout` | azzera il cookie e **revoca il token**: una copia presa altrove smette di funzionare subito |
| `GET` | `/api/auth/me` | utente della sessione corrente |
| `GET` `POST` | `/api/clienti` · `/api/dipendenti` · `/api/siti` · `/api/timesheet` | elenco semplice · creazione |
| `POST` | `/api/{risorsa}/ricerca` | ricerca paginata con filtri → `PaginaResponse<T>` |
| `GET` `PUT` `PATCH` `DELETE` | `/api/{risorsa}/{id}` | dettaglio · aggiornamento · eliminazione |
| `GET` | `/api/dipendenti/scadenze` | contratti in scadenza + finestra di preavviso |
| `POST` | `/api/dipendenti/{id}/ripristino` · `/{id}/rinnovo` | gesti dichiarati, non `PATCH`: hanno permessi e log propri |
| `POST` | `/api/import/timesheet` | multipart; risponde `202`, l'esito non torna al chiamante |
| `POST` `GET` | `/api/chat` · `/api/chat/messaggi` | assistente AI e storico |
| `POST` `GET` `PUT` | `/api/users` · `/api/users/{id}/password` | gestione utenti, solo `ADMIN` |

> Per i test manuali (Postman, Swagger UI): `Authorization: Bearer` **non funziona più**. Va fatto prima
> `POST /api/auth/login`; il client conserva il cookie da solo.

---

## Modello dati

Tabelle in `docker/sqlserver/init/01-schema.sql`: `app_user`, `app_user_role`, `cliente`, `sito`,
`dipendente`, `timesheet`, `chat_messaggio`.

```
cliente 1───* sito 1───* timesheet *───1 dipendente
app_user 1───* app_user_role
```

Le entity mappano i nomi delle colonne con `@Column(name="snake_case")`.

**Stato del dipendente** (derivato, mai salvato in colonna):

| Stato | Condizione | Utilizzabile |
|---|---|---|
| `ATTIVO` | non eliminato, scadenza nulla o oltre il preavviso | sì |
| `IN_SCADENZA` | scadenza entro 15 giorni | **sì** — è solo un avviso |
| `SCADUTO` | `dataScadenza < oggi` (scadenza inclusiva: l'ultimo giorno si lavora) | no |
| `ELIMINATO` | `eliminato = true` | no |

Regole di dominio da conoscere: un dipendente `SCADUTO` o `ELIMINATO` è **in sola lettura** (torna
modificabile solo passando da rinnovo o ripristino); il codice fiscale è `UNIQUE` e ricreare un dipendente
con il CF di un eliminato **riattiva la riga esistente** invece di crearne una nuova — quella `POST`
restituisce quindi un id già esistente; i timesheet già a database non vengono rivalidati retroattivamente.

---

## Convenzioni di sviluppo

**Backend**
- Ogni nuova entity richiede la sua tabella scritta a mano in `01-schema.sql`.
- Le regole di dominio stanno **sull'entity, non nel service**: l'import da RabbitMQ scrive i timesheet senza
  passare dai service, e una regola messa lì verrebbe saltata proprio sulla strada da cui entrano i dati in massa.
- `listAll()` e `count()` di Panache restituiscono **anche gli eliminati**: per ciò che finisce a video si passa
  dai metodi del repository che filtrano per stato.
- Le cache di DTO con stato derivato devono avere un **TTL**, altrimenti una risposta calcolata ieri continua a
  dire "attivo" su un contratto finito stanotte.
- Gli errori passano dagli exception mapper in `dominio/handler`; i 500 non espongono query né percorsi.

**Frontend** — pattern di sezione, applicato a clienti, dipendenti, siti e timesheet e da replicare:
- `sections/<nome>/routes.ts` con path relativi, montati con `loadChildren` lazy;
- `<nome>-api.service.ts` → solo HTTP, `providedIn: 'root'`;
- `<nome>-query.service.ts` → stato della pagina con signals, dichiarato nei `providers` **del componente
  pagina** e non in root: lo stato vive quanto la pagina;
- componenti figli presentazionali (`input()` / `output()`), la pagina fa solo da collante;
- costanti, enum e interfacce nelle rispettive cartelle; i testi delle conferme in `constants/messaggi-*.constant.ts`.

Inoltre:
- **`AppRoute`** (`shared/enums/app-route.enum.ts`) è l'unica fonte di verità per i path.
- **Accento di sezione**: un colore per sezione, dichiarato una volta sola in `styles.scss` come
  `--accento-<sezione>` (clienti viola, dipendenti verde, siti ambra, timesheet azzurro). La pagina lo riprende
  sul proprio `:host` con `--accento` e `--mat-sys-primary`. Va usato **diluito** (5–20% con `color-mix`) su
  bordi, intestazioni e hover; saturo solo su due segni piccoli. Niente superfici colorate grandi.
- **Conferme**: sempre `ConfermaService.chiedi({...})`, mai `MatDialog` a mano. La finestra è generica e senza
  colori propri, identica in tutta l'applicazione.

---

## Stato del progetto

**Completo**
- Infrastruttura Docker (DB, broker, observability)
- Backend: CRUD delle quattro anagrafiche, ricerca paginata, RBAC + JWT su cookie, import Excel asincrono con
  DLQ, assistente AI con function calling, cache, health/metriche/trace
- Frontend: login, guscio autenticato, dashboard, sezioni Clienti, Dipendenti, Siti e upload Timesheet,
  assistente AI

**Da fare**
- Pagina di riepilogo dei timesheet: non esiste ancora, e con la semplificazione delle card della dashboard
  non ha più alcun punto d'ingresso in interfaccia — quando si farà, va aggiunta una navigazione dentro la
  sezione Timesheet.
- **Debito tecnico noto**: `clienti-tabella.scss`, `dipendenti-tabella.scss` e `siti-tabella.scss` sono tre
  copie quasi identiche (differiscono per `min-width`, larghezza della colonna azioni e colonne evidenziate).
  Va estratto un partial SCSS condiviso.
- Il backend non ha ancora test: `be/src/test` non esiste.
