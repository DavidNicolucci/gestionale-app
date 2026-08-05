# Guida al routing Angular — pattern di riferimento

Pattern di routing per applicazioni Angular 19+ (standalone, zoneless, signals), pensato
come riferimento riutilizzabile su qualsiasi progetto.

Nel documento si usano questi segnaposto, da sostituire con i nomi reali del progetto:

| Segnaposto | Significato | Esempio |
|---|---|---|
| `APP_ROUTE` | enum centrale dei path di navigazione | `GESTIONALE_ROUTE` |
| `<area>` | primo livello di navigazione (tab / macro-area) | `gestione` |
| `<sezione>` | raggruppamento di feature dentro un'area | `anagrafiche` |
| `<feature>` | la singola feature = un nodo di routing | `clienti` |
| `@/` | alias di path verso `src/app` | configurato in `tsconfig.json` |

L'esempio filo conduttore è la feature **`clienti`**
(`src/app/sections/anagrafiche/pages/clienti`) e il suo `ClientiComponent`.

---

## 1. La catena di routing: 4 livelli, sempre lazy

Il progetto non ha un unico file di rotte: **ogni livello espone un proprio `routes.ts`**
caricato in lazy dal livello superiore con `loadChildren`. I 4 livelli non sono un dogma:
se l'app non ha il concetto di "tab" si collassano a 3, ma la regola "un `routes.ts` per
livello, sempre lazy" resta.

### Livello 1 — `src/app/app.routes.ts`

Unico `component` non-lazy (il layout applicativo), `authGuard` sul root, tutti i figli
lazy e protetti da `permissionGuard`. Il `path` dei figli **usa direttamente l'enum**,
perché a questo livello path relativo e path assoluto coincidono:

```typescript
export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    resolve: [headerLayoutResolver],
    children: [
      {
        path: APP_ROUTE.GESTIONE,                 // 'gestione'
        canActivate: [permissionGuard],
        loadChildren: () => import('./tabs/gestione/routes').then((r) => r.routes)
      },
      { path: '**', redirectTo: APP_ROUTE.GESTIONE, pathMatch: 'full' }
    ]
  }
];
```

### Livello 2 — `tabs/<area>/routes.ts`

La home dell'area su `path: ''`, poi un `loadChildren` per ogni sezione. Da qui in poi i
path sono **stringhe letterali**, non l'enum, perché sono segmenti relativi:

```typescript
export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/gestione-home/gestione-home').then(c => c.GestioneHome) },
  { path: 'anagrafiche', loadChildren: () => import('@/sections/anagrafiche/routes').then(r => r.routes) },
  { path: '**', redirectTo: '' }
];
```

### Livello 3 — `sections/<sezione>/routes.ts`

Stessa struttura, ma qui compare la **guard di sezione**: si mette sul nodo
`loadChildren`, non sulle singole pagine, così protegge tutto il sottoalbero in un punto
solo.

```typescript
{
  path: 'clienti',
  resolve: { headerLayoutResolver },
  canActivate: [clientiGuard],
  loadChildren: () => import('./pages/clienti/routes').then((r) => r.clientiRoutes)
}
```

### Livello 4 — `pages/<feature>/routes.ts`

Le pagine concrete della feature. È **l'unico livello dove compaiono i parametri di rotta
e il `data`**:

```typescript
export const clientiRoutes: Routes = [
  { path: '', resolve: { headerLayoutResolver },
    loadComponent: () => import('./clienti.component').then((c) => c.ClientiComponent) },

  { path: 'nuovo-cliente', resolve: { headerLayoutResolver },
    loadComponent: () => import('./pages/crea-modifica-cliente/crea-modifica-cliente.component'),
    data: { headerLayout: HEADER_LAYOUT.MEDIUM, headerTitle: 'Nuovo cliente', headerOverTitle: '', view: 'NEW' } },

  { path: 'modifica-cliente/:idCliente', resolve: { headerLayoutResolver },
    loadComponent: () => import('./pages/crea-modifica-cliente/crea-modifica-cliente.component'),
    data: { headerLayout: HEADER_LAYOUT.MEDIUM, headerTitle: 'Modifica cliente', headerOverTitle: '', view: 'EDIT' } },

  { path: 'dettaglio/:idCliente', resolve: { headerLayoutResolver },
    loadComponent: () => import('./pages/dettaglio-cliente/dettaglio-cliente.component')
      .then((c) => c.DettaglioClienteComponent) }
];
```

Due convenzioni che convivono nel `loadComponent`:

- `import('...').then(c => c.NomeComponent)` → il componente è export **nominale**
- `import('...')` senza `.then` → il componente è `export default`

> Conviene scegliere **una sola** delle due convenzioni nel nuovo progetto e applicarla
> ovunque. L'export nominale è preferibile: rende esplicito cosa si sta caricando e
> sopravvive meglio ai rename.

Il `data` di rotta serve a due cose: alimentare l'header tramite resolver, e **riusare lo
stesso componente per creazione e modifica** tramite `view: 'NEW' | 'EDIT'`.

Il `resolve: { headerLayoutResolver }` è la parte più specifica del progetto originale:
se la nuova app non ha un header dinamico guidato dalle rotte, si elimina insieme al
`data` di header e resta solo `path` + `loadComponent` (+ `data` per il `view`).

---

## 2. `APP_ROUTE`: un solo enum con i path **assoluti**

Il punto chiave, e la cosa che confonde di più all'inizio: nei `routes.ts` i path sono
**relativi** (segmenti), mentre nell'enum sono **assoluti dalla root**. L'enum non serve a
definire le rotte, serve a **navigarci**.

```typescript
// src/app/utils/enums/routes.enum.ts
export enum APP_ROUTE {
  // ***** AREA GESTIONE *****
  GESTIONE     = 'gestione',
  ANAGRAFICHE  = 'gestione/anagrafiche',

  // ***** CLIENTI *****
  CLIENTI                  = 'gestione/anagrafiche/clienti',
  CREA_CLIENTE             = `${CLIENTI}/nuovo-cliente`,
  CONFERMA_CREA_CLIENTE    = `${CLIENTI}/nuovo-cliente/conferma`,
  MODIFICA_CLIENTE         = `${CLIENTI}/modifica-cliente`,
  CONFERMA_MODIFICA_CLIENTE = `${CLIENTI}/modifica-cliente/conferma`,
  DETTAGLIO_CLIENTE        = `${CLIENTI}/dettaglio`,
}
```

### Regole operative

- **Composizione con template literal** a partire dalla chiave padre
  (`${CLIENTI}/nuovo-cliente`), mai riscrivere il path per intero. All'interno dello
  stesso enum si può referenziare la chiave direttamente; da un blocco a un altro si usa
  la forma `${APP_ROUTE.ANAGRAFICHE}/...`. Le due forme sono equivalenti.
- **I parametri NON stanno nell'enum**: `DETTAGLIO_CLIENTE` finisce con `/dettaglio`,
  l'id viene passato come secondo elemento dell'array in `navigate()`.
- Le voci si raggruppano per dominio con blocchi commentati
  (`// ***** CLIENTI *****`): è l'unica cosa che tiene leggibile un enum che, a regime,
  arriva a centinaia di righe.
- L'enum è **la sola fonte di verità per la navigazione**: nessun
  `navigate(['/gestione/...'])` con stringa scritta a mano nel codice.

### Anti-pattern da evitare (visti sul progetto originale)

- **Voci duplicate** con lo stesso valore sotto nomi diversi.
- **Doppi slash** da composizione sbagliata (`sezione//dettaglio`).
- **Voci scollegate dalle rotte reali**, rimaste dopo un refactor.

Regola per prevenirli: **una voce di enum per ogni `path` dichiarato, aggiunte nello
stesso commit**. Se un path cambia, cambia anche l'enum, nello stesso diff.

---

## 3. Le 4 forme di `router.navigate()`

Il router si inietta sempre con `inject()`, `private readonly`, e ogni chiamata è
preceduta da `void` (o `await` se il metodo è `async`) perché `navigate()` ritorna una
`Promise`.

```typescript
private readonly router = inject(Router);
```

### a) Navigazione semplice — solo enum

```typescript
protected addNuovoCliente(): void {
  void this.router.navigate([APP_ROUTE.CREA_CLIENTE]);
}
```

### b) Con parametro di rotta + `queryParams`

L'id è un elemento separato dell'array, i dati di supporto vanno in query string:

```typescript
protected async onDetailClick(rowIndex: number): Promise<void> {
  const row = this.clienti()[rowIndex];
  await this.router.navigate([APP_ROUTE.DETTAGLIO_CLIENTE, row.id], {
    queryParams: { dataInizio: row.dataInizio, dataFine: row.dataFine }
  });
}
```

→ produce `/gestione/anagrafiche/clienti/dettaglio/42?dataInizio=...&dataFine=...`, che
combacia con `path: 'dettaglio/:idCliente'`.

### c) Con `state`

Payload in memoria, non in URL, tipicamente per passare un oggetto a una pagina di
conferma:

```typescript
const route = isEdit ? APP_ROUTE.CONFERMA_MODIFICA_CLIENTE : APP_ROUTE.CONFERMA_CREA_CLIENTE;
void this.router.navigate([route], { state: datiConferma });
```

> Lo `state` **non sopravvive al reload della pagina**: va sempre accompagnato dal
> fallback del punto (d).

### d) Navigazione difensiva / redirect

Se mancano i dati necessari si torna alla lista invece di mostrare una pagina rotta:

```typescript
if (!dataInizio || !dataFine) {
  console.error('Parametri mancanti per recuperare il dettaglio');
  void this.router.navigate([APP_ROUTE.CLIENTI]);
  return;
}
```

Stesso schema nelle guard (`navigate([APP_ROUTE.GESTIONE]); return false;`) e nelle pagine
di conferma, che rimandano alla lista se `history.state` è vuoto.

---

## 4. Come si **leggono** i dati di rotta

Prerequisito in `app.config.ts`: `withComponentInputBinding()`, senza il quale il binding
dei param sugli `input()` non funziona.

```typescript
provideRouter(
  routes,
  withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
  withViewTransitions(),
  withComponentInputBinding()
)
```

| Cosa | Come si legge |
|---|---|
| Param di rotta (`:idCliente`) | `input.required<string>()` con **lo stesso nome del param** |
| Query param | `route.snapshot.queryParamMap.get('dataInizio')` |
| `data` di rotta | `route.snapshot.data['view']` |
| `state` | `history.state as MiaInterface \| undefined` |

```typescript
export class DettaglioClienteComponent {
  idCliente = input.required<string>();   // ← binding automatico da :idCliente
  private readonly route = inject(ActivatedRoute);
}
```

> Con `withComponentInputBinding()` anche i query param e il `data` possono essere
> bindati come `input()`, se il nome coincide. Lo `snapshot` resta necessario quando il
> nome differisce o quando serve leggere il valore in modo imperativo dentro un metodo.

### Il "torna indietro"

Non `history.back()`, ma un componente dedicato che riceve l'enum come input: rende la
destinazione esplicita e indipendente da come l'utente è arrivato sulla pagina.

```html
<app-go-back [to]="APP_ROUTE.ANAGRAFICHE" />
```

`GoBackComponent` fa `navigate([this.to()])` se `to` è valorizzato, altrimenti
`window.history.back()`. Per usarlo dal template il componente deve esporre l'enum come
membro protetto:

```typescript
protected readonly APP_ROUTE = APP_ROUTE;
```

---

## 5. Struttura delle cartelle

Regola generale: **una cartella = una rotta**. Tutto ciò che serve solo a quella rotta
vive dentro la sua cartella, e le sotto-rotte stanno in `pages/`.

```
pages/clienti/                          ← la feature = 1 nodo di routing
├── routes.ts                           ← SEMPRE alla radice della feature
├── clienti.component.ts / .html / .css ← componente della rotta ''  (stesso nome della cartella)
├── components/                         ← componenti usati SOLO da clienti.component
│   └── clienti-filtri/
├── configs/                            ← config tabelle (clienti-table-config.ts)
├── guards/                             ← guard specifiche della feature
├── interfaces/                         ← interfacce dei form (clienti-filtri-form.ts)
├── models/                             ← model + DTO (request/response)
├── modals/                             ← modali della feature
├── services/                           ← api / query / table / action / builder
└── pages/                              ← SOTTO-ROTTE, ognuna ricorsivamente identica
    ├── crea-modifica-cliente/
    │   ├── crea-modifica-cliente.component.ts / .html
    │   └── components/  configs/  interfaces/  models/  services/
    ├── conferma-cliente/
    │   ├── conferma-cliente.component.ts / .html
    │   └── configs/  interfaces/  services/
    └── dettaglio-cliente/
```

### Punti da rispettare

- **`routes.ts` sempre nella radice della cartella**, mai in una sottocartella `routing/`.
  Nome dell'export: `<feature>Routes` a livello feature (`clientiRoutes`), `routes`
  generico a livello area/sezione.
- **`pages/` = ricorsione**: una sotto-pagina ha la stessa identica struttura interna
  della pagina padre, con i propri `configs/`, `interfaces/`, `services/` quando servono
  solo a lei.
- **Niente `shared/` locale**: se una cosa serve a più di una feature, sale a `src/app/`
  (`components/`, `services/`, `models/`, `interfaces/`, `utils/`). L'enum delle rotte sta
  in `utils/enums/`, mai dentro una feature.
- **Naming**: il componente ha lo stesso nome della cartella; i servizi hanno suffisso di
  ruolo (`-api`, `-query`, `-table`, `-action`, `-cell-builder`); i model hanno
  `-request.model.ts` / `-response.model.ts`.
- **Import sempre con alias `@/`**, path assoluto anche all'interno della stessa feature.
- Cartelle vuote create in anticipo (`guards/`, `mocks/`, `modals/`): lo scheletro si crea
  completo. Se questa parte risulta rumorosa, l'alternativa è crearle on-demand — l'unico
  vincolo reale è che i nomi siano sempre gli stessi.

---

## 6. Checklist per aggiungere una rotta

1. Crea la cartella sotto `pages/` con lo scheletro
   (`components/ configs/ interfaces/ models/ services/ pages/`).
2. Aggiungi il record in `routes.ts` della feature: `path` **relativo**, `loadComponent`,
   `resolve` se l'app usa i resolver di header, e `data` se serve titolo / `view`.
3. Aggiungi la voce in `APP_ROUTE` come **path assoluto composto** dal padre, senza il
   segmento `:param`.
4. Nel componente: `private readonly router = inject(Router)` +
   `protected readonly APP_ROUTE = APP_ROUTE` se il template ne ha bisogno.
5. Naviga con `void this.router.navigate([APP_ROUTE.X, id], { queryParams })`; usa `state`
   solo per payload effimeri e prevedi **sempre** il fallback alla lista se lo state manca
   al reload.
6. Se la pagina è protetta, la guard va sul nodo `loadChildren` della sezione, non sulla
   singola pagina.

### Prerequisiti globali del progetto

- `withComponentInputBinding()` in `provideRouter`, altrimenti `input.required<string>()`
  sui param di rotta non funziona e si deve ripiegare su `route.snapshot.paramMap`.
- Alias `@/` → `src/app` configurato in `tsconfig.json` (`compilerOptions.paths`).
- Un `HEADER_LAYOUT` (o equivalente) solo se si adotta il pattern header-guidato-da-rotta;
  altrimenti si omettono `resolve` e `data` di header ovunque.
