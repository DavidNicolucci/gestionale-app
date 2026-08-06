# Guida ai form di creazione/modifica — pattern da replicare

Descrizione di un pattern per le pagine "crea / modifica" in Angular 19+
(standalone, zoneless, signals, Reactive Forms tipizzati), da riutilizzare come
riferimento in qualsiasi progetto.

Idea di fondo: **il componente pagina non costruisce, non valida e non trasforma nulla.**
Orchestra soltanto. Costruzione del form, validazione e gestione delle righe stanno in tre
servizi separati, forniti a livello di componente; la trasformazione verso il backend sta
nel model.

### Convenzioni di questa guida

Il dominio degli esempi è **fittizio e sostituibile**: una `Prenotazione` con un intervallo
data/ora, a cui si associano N `Partecipanti`, ognuno con un proprio intervallo data/ora
modificabile in tabella. Sostituisci i nomi con quelli del tuo dominio:

| Nel testo | Significa |
|---|---|
| `<sezione>` | la sezione/area applicativa (es. `gestione-prenotazioni`) |
| `<feature>` / `Prenotazione` | l'entità principale del form |
| `<riga>` / `Partecipante` | l'entità figlia, una riga del `FormArray` |
| `app-date-picker`, `app-searchbar`, … | i controlli del **tuo** design system |
| `APP_ICONS`, `APP_ROUTE` | le costanti del tuo progetto |

Le interfacce `SearchbarItemInterface`, `SelectComboItemInterface` e `DisabledDatesInterface`
sono quelle esposte dai controlli del tuo design system. Forma minima assunta qui:

```typescript
interface SelectComboItemInterface { label: string; value: string; }
interface SearchbarItemInterface {
  label: string;
  value: string;
  cardAdditionalData?: { id: number; [k: string]: unknown };
}
interface DisabledDatesInterface { min?: string; max?: string; }
```

---

## 1. Struttura delle cartelle

Una pagina di crea/modifica è una cartella sotto `pages/` della feature, con sempre le
stesse sottocartelle:

```
sections/<sezione>/pages/<feature>/
├── models/                                  # model condivisi tra le pagine della feature
├── services/
│   ├── <feature>-api.service.ts             # solo HTTP, providedIn: 'root'
│   └── <feature>-query.service.ts           # stato + orchestrazione chiamate, providedIn: 'root'
├── routes.ts                                # rotte della feature (nuovo / modifica / conferma)
│
└── pages/crea-modifica-<feature>/           # LA PAGINA DEL FORM
    ├── crea-modifica-<feature>.component.ts     # orchestratore
    ├── crea-modifica-<feature>.component.html
    │
    ├── interfaces/                          # SOLO tipi dei FormGroup e dello stato UI
    │   ├── crea-modifica-<feature>-form.interface.ts   # form principale
    │   ├── <riga>-row-form.interface.ts                # form di UNA riga del FormArray
    │   ├── compilazione-rapida-form.interface.ts       # form "applica a tutti"
    │   └── <riga>-row-state.interface.ts               # stato errori/disabled per riga
    │
    ├── models/                              # DTO request/response + model di riga tabella
    │   ├── salva-<feature>-request.model.ts
    │   ├── salva-<feature>-response.model.ts
    │   ├── dettaglio-<feature>-response.model.ts
    │   └── <riga>-in-tabella.model.ts
    │
    ├── services/                            # 3 servizi, providedIn ASSENTE (scope pagina)
    │   ├── crea-modifica-form.service.ts        # costruisce e popola i form
    │   ├── crea-modifica-validation.service.ts  # validazioni cross-field + stato errori
    │   └── crea-modifica-<righe>.service.ts     # tabella righe: sync, filtro, paginazione
    │
    ├── components/                          # componenti presentazionali, zero logica
    │   ├── crea-modifica-<feature>-form/    # blocco "dati principali"
    │   ├── <selezione-entita>/              # blocco di selezione (searchbar/select)
    │   └── tabella-<righe>/                 # tabella righe + salva
    │
    └── configs/                             # config tabelle statiche (se servono)
```

### Regole per decidere dove va un file

| Se il file… | va in… |
|---|---|
| descrive la **forma di un FormGroup** (`FormControl<T \| null>`) | `interfaces/` |
| descrive uno **stato UI** (flag errore, testo errore, date disabilitate) | `interfaces/` |
| **costruisce un body** per il backend o mappa una response | `models/` |
| ha **logica** (crea form, valida, sincronizza righe) | `services/` della pagina |
| fa **HTTP** | `services/` della feature (`*-api.service.ts`) |
| tiene **stato condiviso tra pagine** (es. dati per la pagina di conferma) | `services/` della feature (`*-query.service.ts`) |
| è **solo template + input/output** | `components/` |

Regola pratica: se in un file `components/` compare un `inject()` di qualcosa che non sia
`DestroyRef`, quasi sempre quella logica va spostata in un servizio della pagina.

---

## 2. Livello 1 — le interfacce dei form

Tre form separati, tre interfacce. Mai `FormGroup` non tipizzato.

**Form principale** — `interfaces/crea-modifica-prenotazione-form.interface.ts`:

```typescript
export interface CreaModificaPrenotazioneFormInterface {
  idPrenotazione: FormControl<number | null>;      // valorizzato solo in EDIT
  idTipologia: FormControl<number | null>;         // scelta da ricerca remota
  dataInizio: FormControl<string | null>;
  oraInizio: FormControl<string | null>;
  dataFine: FormControl<string | null>;
  oraFine: FormControl<string | null>;
  idSottocategoria: FormControl<string | null>;    // campo dipendente da un altro control
  partecipanti: FormControl<SearchbarItemInterface[] | null>;          // selezione multipla
  partecipantiRows: FormArray<FormGroup<PartecipanteRowFormInterface>>; // una riga per selezione
}
```

Da notare il pattern chiave: **un `FormControl` con la lista selezionata + un `FormArray`
di righe derivate**. Il control è la "sorgente", il FormArray è la proiezione editabile.

**Form di una riga** — `interfaces/partecipante-row-form.interface.ts`. I campi `orig*`
conservano il valore iniziale in modifica, per calcolare il flag `modificato` in fase di
salvataggio:

```typescript
export interface PartecipanteRowFormInterface {
  idRiga: FormControl<number | null>;
  dataInizio: FormControl<string | null>;
  oraInizio: FormControl<string | null>;
  dataFine: FormControl<string | null>;
  oraFine: FormControl<string | null>;
  origDataInizio: FormControl<string | null>;   // snapshot per il diff
  origOraInizio: FormControl<string | null>;
  origDataFine: FormControl<string | null>;
  origOraFine: FormControl<string | null>;
}
```

**Stato UI di una riga** — `interfaces/partecipante-row-state.interface.ts`. Gli errori
cross-field non stanno negli `errors` del control: stanno in un oggetto di stato parallelo,
perché il template deve poter mostrare messaggi diversi per lo stesso campo.

```typescript
export interface PartecipanteRowStateInterface {
  disabledInizio: DisabledDatesInterface;
  disabledFine: DisabledDatesInterface;
  hasErrorDataInizio: boolean;
  errorDataInizio: string;
  hasErrorDataFine: boolean;
  errorDataFine: string;
  hasErrorOraInizio: boolean;
  errorOraInizio: string;
  hasErrorOraFine: boolean;
  errorOraFine: string;
}
```

---

## 3. Livello 2 — i tre servizi di pagina

Tutti e tre sono `@Injectable()` **senza `providedIn`** e vengono dichiarati nei
`providers` del componente pagina: nascono e muoiono con la pagina, quindi niente stato
sporco al rientro.

```typescript
providers: [CreaModificaFormService, CreaModificaValidationService, CreaModificaPartecipantiService]
```

### 3.1 `crea-modifica-form.service.ts` — costruzione e population

Espone metodi `create*` (uno per ogni form/riga) e metodi `populate*` / `patch*` per la
modalità modifica. Non contiene stato.

```typescript
@Injectable()
export class CreaModificaFormService {

  createMainForm(): FormGroup<CreaModificaPrenotazioneFormInterface> {
    return new FormGroup<CreaModificaPrenotazioneFormInterface>({
      idPrenotazione: new FormControl(null),
      idTipologia: new FormControl(null, Validators.required),
      dataInizio: new FormControl(null, Validators.required),
      // ...
      partecipanti: new FormControl(null),
      partecipantiRows: new FormArray<FormGroup<PartecipanteRowFormInterface>>([])
    });
  }

  createCompilazioneRapidaForm(): FormGroup<CompilazioneRapidaFormInterface> { /* ... */ }

  createRowFormGroup(): FormGroup<PartecipanteRowFormInterface> { /* ... */ }

  /** Modalità EDIT: dal DTO di dettaglio ai control del form */
  populateFromDettaglio(form, dettaglio): { tipologiaOption, partecipantiItems } { /* ... */ }

  /** Modalità EDIT: popola una riga e ne salva lo snapshot orig* */
  patchRowFromDettaglio(rowForm, riga): void {
    rowForm.patchValue({
      dataInizio: riga.dataInizio,
      // ...
      origDataInizio: riga.dataInizio,   // snapshot per il diff
      // ...
    });
  }
}
```

`populateFromDettaglio` **restituisce** ciò che serve al componente (es. l'opzione da
mettere nella select), invece di toccare i signal del componente: il servizio resta puro.

### 3.2 `crea-modifica-validation.service.ts` — validazione cross-field

Contiene tutto ciò che i `Validators` di Angular non possono esprimere: confronti tra
campi, tra righe, con "adesso". Espone **tre signal** che il template consuma direttamente:

```typescript
@Injectable()
export class CreaModificaValidationService {
  readonly rowStates = signal<PartecipanteRowStateInterface[]>([]);       // errori per riga
  readonly isSalvaDisabled = signal<boolean>(true);                       // abilitazione submit
  readonly statoCompilazioneRapida = signal<PartecipanteRowStateInterface>(...);

  private readonly destroyRef = inject(DestroyRef);

  /** Sottoscrive i valueChanges di una riga; chiamato alla creazione della riga */
  setupRowValidation(rowForm: FormGroup<PartecipanteRowFormInterface>, rowIndex: number): void {
    rowForm.controls.dataInizio.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))     // destroyRef esplicito: non siamo in un injection context
      .subscribe(val => { this.validateRowDataInizio(rowForm, rowIndex, val); this.validateRowOrari(rowForm, rowIndex); });
    // ... stessa cosa per oraInizio / dataFine / oraFine
  }

  /** Unico punto che decide se il submit è abilitato */
  updateSalvaDisabled(form, formArray): void {
    if (!this.isMainFormValid(form) || this.hasMainFormErrors(form)) { this.isSalvaDisabled.set(true); return; }
    if (!formArray.length || !this.areAllRowsFilled(formArray) || this.hasRowErrors(formArray)) { this.isSalvaDisabled.set(true); return; }
    this.isSalvaDisabled.set(false);
  }
}
```

I messaggi stanno in una costante in cima al file, non sparsi nel codice:

```typescript
const ERROR_MESSAGES = {
  DATA_NON_VALIDA: 'La data selezionata non è valida',
  DATA_FINE_PRIMA_INIZIO: 'La data fine non può essere precedente alla data inizio',
  ORA_PRIMA_ATTUALE: 'L\'orario inserito non può essere precedente all\'orario attuale'
} as const;
```

Punti da replicare:
- gli aggiornamenti di `rowStates` sono **immutabili** (`const states = [...this.rowStates()]`,
  poi `states[i] = {...}`, poi `.set(states)`) — obbligatorio con OnPush + signals;
- `disablePastDateTimeControls()` disabilita i control con data/ora già passata in modifica,
  e ogni check di errore è guardato da `ctrl.enabled` così i campi disabilitati non bloccano
  il salvataggio.

### 3.3 `crea-modifica-partecipanti.service.ts` — righe, filtro, paginazione

Tiene lo stato della tabella e tutta la derivazione con `computed`:

```typescript
@Injectable()
export class CreaModificaPartecipantiService {
  readonly partecipantiInTabella = signal<PartecipanteInTabellaModel[]>([]);
  readonly currentPage = signal<number>(1);
  readonly filtroRicerca = signal<string>('');
  readonly pageSize = signal<number>(10);

  readonly indiciFiltrati   = computed(() => /* indici che passano il filtro */);
  readonly totalPages       = computed(() => Math.ceil(this.indiciFiltrati().length / this.pageSize()));
  readonly showPaginator    = computed(() => this.indiciFiltrati().length > this.pageSize());
  readonly paginatedIndices = computed(() => /* slice della pagina corrente */);

  constructor(
    private readonly formService: CreaModificaFormService,
    private readonly validationService: CreaModificaValidationService
  ) {}
}
```

Il metodo centrale è `syncFromSelectedItems()`: tiene allineati **tre array paralleli**
(model di tabella, `FormArray`, `rowStates`) partendo dalla lista selezionata, riutilizzando
le righe esistenti così i valori già digitati non si perdono:

```typescript
syncFromSelectedItems(selectedItems: SearchbarItemInterface[], formArray: FormArray<...>): void {
  const current = this.partecipantiInTabella();
  const currentStates = this.validationService.rowStates();

  // mappa value -> coda di indici disponibili: match O(1) e duplicati gestiti dallo shift
  const availableByValue = new Map<string, number[]>();
  current.forEach((row, i) => { /* riempi le code */ });

  const updated: PartecipanteInTabellaModel[] = [];
  const newFormGroups: FormGroup<PartecipanteRowFormInterface>[] = [];
  const newStates: PartecipanteRowStateInterface[] = [];

  for (const item of selectedItems) {
    const existingIndex = availableByValue.get(item.value)?.shift();
    if (existingIndex !== undefined) {
      // riga già presente: preservo FormGroup e stato (quindi i valori digitati)
      updated.push(current[existingIndex]);
      newFormGroups.push(formArray.at(existingIndex));
      newStates.push(currentStates[existingIndex]);
    } else {
      // riga nuova: creo form vuoto, stato di default e attivo la validazione
      const rowForm = this.formService.createRowFormGroup();
      updated.push(PartecipanteInTabellaModel.generateModel(item));
      newFormGroups.push(rowForm);
      newStates.push(this.validationService.createDefaultRowState());
      this.validationService.setupRowValidation(rowForm, newStates.length - 1);
    }
  }

  this.partecipantiInTabella.set(updated);
  formArray.clear();
  newFormGroups.forEach(fg => formArray.push(fg));
  this.validationService.rowStates.set(newStates);
}
```

Corollario elegante: `duplicateAtIndex()` non duplica nulla a mano — inserisce l'item in
`index + 1` nel control, il `valueChanges` richiama `syncFromSelectedItems`, e la copia non
trova più match in coda quindi genera automaticamente una riga nuova con form vuoto.

`removeAtIndex()` invece rimuove dai tre array in parallelo e usa `{ emitEvent: false }`
sul `setValue` per non ri-scatenare il sync.

---

## 4. Livello 3 — il componente pagina (orchestratore)

```typescript
@Component({
  selector: 'crea-modifica-prenotazione',
  imports: [GoBackComponent, PageTitleComponent, TranslatePipe,
            CreaModificaPrenotazioneFormComponent, SelezionePartecipantiComponent,
            TabellaPartecipantiComponent, ReactiveFormsModule],
  templateUrl: './crea-modifica-prenotazione.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],                        // SOLO se il DS usa web component
  providers: [CreaModificaFormService, CreaModificaValidationService, CreaModificaPartecipantiService]
})
export default class CreaModificaPrenotazioneComponent implements OnInit {
```

Note: `export default` perché la rotta usa `loadComponent` senza `.then()`.

### 4.1 Ordine dei membri

```typescript
  // 1. input()
  protected readonly idPrenotazione = input<number | undefined>();

  // 2. form (readonly, assegnati nel constructor)
  protected readonly form: FormGroup<CreaModificaPrenotazioneFormInterface>;
  protected readonly compilazioneRapidaForm: FormGroup<CompilazioneRapidaFormInterface>;
  protected readonly categoriaControl = new FormControl<string | null>(null);  // control fuori dal form

  // 3. signal di stato UI (opzioni delle select, flag)
  protected readonly opzioniTipologie = signal<SelectComboItemInterface[]>([]);
  protected readonly typeAction = signal<TYPE_VIEW>(TYPE_VIEW.NEW);

  // 4. enum esposti al template
  protected readonly APP_ICONS = APP_ICONS;
  protected readonly typeView = TYPE_VIEW;

  // 5. servizi USATI DAL TEMPLATE -> protected
  protected readonly partecipantiService = inject(CreaModificaPartecipantiService);
  protected readonly validationService = inject(CreaModificaValidationService);

  // 6. servizi interni -> private
  private readonly formService = inject(CreaModificaFormService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  // ...
```

Sempre `inject()`, mai constructor injection (unica eccezione: i servizi di pagina che si
iniettano tra loro, vedi 3.3).

### 4.2 Il constructor: solo cablaggio

Il constructor è l'unico posto dove si può usare `takeUntilDestroyed()` senza `DestroyRef`.

```typescript
constructor() {
  this.form = this.formService.createMainForm();
  this.compilazioneRapidaForm = this.formService.createCompilazioneRapidaForm();

  // selezione -> righe tabella
  this.form.controls.partecipanti.valueChanges
    .pipe(takeUntilDestroyed())
    .subscribe(partecipanti => {
      this.partecipantiService.syncFromSelectedItems(partecipanti ?? [], this.partecipantiFormArray);
      this.updateSalvaDisabled();
    });

  // qualsiasi modifica -> ricalcolo abilitazione submit
  this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.updateSalvaDisabled());

  this.validationService.setupValidazioneCompilazioneRapida(this.compilazioneRapidaForm);

  // dipendenza tra campi: la sottocategoria si abilita solo con la categoria scelta
  this.form.controls.idSottocategoria.disable();
  this.categoriaControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(value => {
    this.form.controls.idSottocategoria.reset();
    this.opzioniSottocategorie.set([]);
    value ? this.form.controls.idSottocategoria.enable() : this.form.controls.idSottocategoria.disable();
  });
}

get partecipantiFormArray() {
  return this.form.controls.partecipantiRows;
}
```

### 4.3 `ngOnInit`: caricamento dati e discriminazione NEW/EDIT

La modalità arriva dalla **rotta**, non da un input:

```typescript
async ngOnInit(): Promise<void> {
  this.typeAction.set(this.route.snapshot.data['view']);      // 'NEW' | 'EDIT'
  this.opzioniCategorie.set(await this.listService.getCategorie());

  if (this.typeAction() === TYPE_VIEW.EDIT && this.idPrenotazione()) {
    const dataOraInizio = this.route.snapshot.queryParamMap.get('dataOraInizio');
    const dataOraFine = this.route.snapshot.queryParamMap.get('dataOraFine');
    if (dataOraInizio && dataOraFine) {
      await this.loadPrenotazione(this.idPrenotazione()!, dataOraInizio, dataOraFine);
    }
  }
}
```

`idPrenotazione` arriva come `input()` grazie a `withComponentInputBinding()` (parametro di
rotta `:idPrenotazione` bindato automaticamente all'input omonimo).

Il caricamento in modifica delega tutto ai servizi:

```typescript
private async loadPrenotazione(id: number, dataOraInizio: string, dataOraFine: string): Promise<void> {
  const dettaglio = await this.prenotazioniQueryService.getPrenotazioneById(body);
  const { tipologiaOption } = this.formService.populateFromDettaglio(this.form, dettaglio);
  this.opzioniTipologie.set([tipologiaOption]);     // l'opzione salvata deve esistere nella select

  (dettaglio.righe ?? []).forEach((riga, i) => {
    const rowForm = this.partecipantiFormArray.at(i);   // le righe esistono già: create dal sync
    if (rowForm) {
      this.formService.patchRowFromDettaglio(rowForm, { /* ... */ });
      this.validationService.disablePastDateTimeControls(/* i 4 control della riga */);
    }
  });

  this.validationService.disablePastDateTimeControls(/* i 4 control principali */);
  this.updateSalvaDisabled();
}
```

Ordine importante: `populateFromDettaglio` fa `setValue` su `partecipanti`, il che scatena
il sync e **crea le righe**; solo dopo il ciclo può fare `patchRowFromDettaglio` sulle righe
appena nate.

### 4.4 Ricerche remote: la soglia dei 3 caratteri

Ogni campo di ricerca segue lo stesso schema — soglia, chiamata, mappatura ad `asSelectComboItem` /
`asSearchBarItem` esposto dal model:

```typescript
protected async onTipologiaChange(descrizione: string): Promise<void> {
  if (descrizione.length < 3) { this.opzioniTipologie.set([]); return; }
  const body = TipologiaRequestModel.generateModel(descrizione);
  const response = await this.listService.getTipologie(body);
  this.opzioniTipologie.set(response.risultati.map(t => t.asSelectComboItem));
}
```

### 4.5 Submit e navigazione alla conferma

```typescript
protected async onSalva(): Promise<void> {
  if (this.typeAction() === TYPE_VIEW.EDIT) {
    await this.prenotazioniQueryService.getEditPrenotazione(this.form, this.partecipantiFormArray);
  } else {
    await this.prenotazioniQueryService.getSalvaPrenotazione(this.form, this.partecipantiFormArray);
  }

  const datiConferma: DatiConfermaPrenotazioneInterface = { /* riepilogo */ };
  const route = isEdit ? APP_ROUTE.CONFERMA_MODIFICA_PRENOTAZIONE : APP_ROUTE.CONFERMA_NUOVA_PRENOTAZIONE;
  void this.router.navigate([route], { state: datiConferma });     // dati alla pagina di esito via state
}
```

Il componente **non costruisce il body**: passa `form` + `formArray` al query service.

---

## 5. Livello 4 — i componenti presentazionali

Tre componenti figli, tutti con la stessa firma: ricevono i **`FormGroup`/`FormControl` già
costruiti** via `input.required()` e ributtano fuori gli eventi con `output()`. Non
iniettano servizi, non chiamano API.

```typescript
export class CreaModificaPrenotazioneFormComponent implements OnInit {
  form = input.required<FormGroup<CreaModificaPrenotazioneFormInterface>>();
  opzioniTipologie = input<SelectComboItemInterface[]>([]);
  isEdit = input<boolean>(false);
  onTipologiaChange = output<string>();

  ngOnInit(): void {
    const controls = this.form().controls;
    if (this.isEdit()) controls.idTipologia.disable();       // regola specifica del blocco
    // sottoscrizioni di validazione locale con takeUntilDestroyed(this.destroyRef)
  }
}
```

Nel template si binda sempre con `[formControl]` sul control estratto dal signal, mai con
`formControlName` (i figli non hanno un `formGroup` contenitore):

```html
<app-date-picker
  [disabledDates]="disabledInizio()"
  [errorDescription]="'La data di inizio non può essere precedente al mese corrente' | translate"
  [formControl]="form().controls.dataInizio"
  [hasFormError]="!!form().controls.dataInizio.errors?.['dataPassata']"
  [label]="'Data inizio' | translate"
/>
```

Per la tabella, il `FormArray` viene indicizzato direttamente e lo stato errore arriva dal
signal del validation service:

```html
@for (i of paginatedIndices(); track i) {
  <tr>
    <td>{{ partecipantiInTabella()[i].label }}</td>
    <td>
      <app-date-picker
        [disabledDates]="rowStates()[i].disabledInizio"
        [errorDescription]="rowStates()[i].errorDataInizio"
        [formControl]="partecipantiFormArray().controls[i].controls.dataInizio"
        [hasFormError]="rowStates()[i].hasErrorDataInizio"
      />
    </td>
    <!-- ... -->
  </tr>
}
```

Il template della pagina si limita a cablare figli, signal dei servizi e handler:

```html
<crea-modifica-prenotazione-form
  (onTipologiaChange)="onTipologiaChange($event)"
  [form]="form"
  [isEdit]="typeAction() === typeView.EDIT"
  [opzioniTipologie]="opzioniTipologie()"
/>

@if (partecipantiService.partecipantiInTabella().length) {
  <tabella-partecipanti
    (onSalva)="onSalva()"
    (onPageChange)="partecipantiService.currentPage.set($event)"
    [isSalvaDisabled]="validationService.isSalvaDisabled()"
    [paginatedIndices]="partecipantiService.paginatedIndices()"
    [partecipantiFormArray]="partecipantiFormArray"
    [rowStates]="validationService.rowStates()"
  />
}
```

Nota: gli attributi nei template sono ordinati **output prima, poi input in ordine
alfabetico**. È una convenzione: mantienila coerente su tutto il progetto.

---

## 6. Livello 5 — il model di request

La trasformazione form → body sta in uno `static generateModel()` sul model, con
`class-transformer` per le conversioni (qui le date in UTC).

```typescript
export class SalvaPrenotazioneRequestModel {
  idTipologia?: number;
  idPrenotazione?: number;
  @Transform(transformDateToUTC) dataOraInizio!: string;
  @Transform(transformDateToUTC) dataOraFine!: string;
  @Type(() => RigaPrenotazioneModel) righe!: RigaPrenotazioneModel[];

  static generateModel(
    form: FormGroup<CreaModificaPrenotazioneFormInterface>,
    partecipantiRows: FormArray<FormGroup<PartecipanteRowFormInterface>>,
    isEdit: boolean = false
  ): SalvaPrenotazioneRequestModel {
    const raw = form.getRawValue();          // getRawValue: include anche i control disabilitati

    const righe = (raw.partecipanti ?? []).map((p, i) => {
      const row = partecipantiRows.at(i).getRawValue();
      const isExistingRow = row.origDataInizio !== null;
      const modificato = isEdit && isExistingRow && (
        row.dataInizio !== row.origDataInizio || row.oraInizio !== row.origOraInizio ||
        row.dataFine !== row.origDataFine   || row.oraFine   !== row.origOraFine
      );
      return {
        id: row.idRiga ?? null,
        idAnagrafica: p.cardAdditionalData?.id ?? p.value,
        dataOraInizio: DateUtils.combineDateAndTime(row.dataInizio, row.oraInizio) ?? '',
        dataOraFine:   DateUtils.combineDateAndTime(row.dataFine, row.oraFine) ?? '',
        modificato
      };
    });

    const plainObj: Record<string, unknown> = { /* date principali + righe */ };
    if (isEdit) plainObj['idPrenotazione'] = raw.idPrenotazione;
    else plainObj['idTipologia'] = SalvaPrenotazioneRequestModel.extractId(raw.idTipologia);

    return plainToInstance(SalvaPrenotazioneRequestModel, plainObj);
  }
}
```

Tre cose da portarsi dietro: **`getRawValue()`** (non `.value`, che scarta i disabilitati),
il **flag `modificato`** calcolato dal diff con gli `orig*`, e il **campo diverso tra create
e edit** (`idTipologia` vs `idPrenotazione`) gestito dentro il model, non nel componente.

Il query service è un passacarte di due righe:

```typescript
async getSalvaPrenotazione(form, partecipantiRows): Promise<void> {
  const body = SalvaPrenotazioneRequestModel.generateModel(form, partecipantiRows);
  await this.prenotazioniApiService.salvaPrenotazione(body);
}

async getEditPrenotazione(form, partecipantiRows): Promise<void> {
  const body = SalvaPrenotazioneRequestModel.generateModel(form, partecipantiRows, true);
  await this.prenotazioniApiService.editPrenotazione(body);
}
```

---

## 7. Le rotte: un solo componente per NEW e EDIT

Stesso `loadComponent`, `data.view` diverso. Il componente legge `route.snapshot.data['view']`.

```typescript
// pages/prenotazioni/routes.ts
{
  path: 'nuova-prenotazione',
  loadComponent: () => import('./pages/crea-modifica-prenotazione/crea-modifica-prenotazione.component'),
  data: { headerTitle: 'Nuova prenotazione', view: 'NEW' }
},
{
  path: 'modifica-prenotazione/:idPrenotazione',
  loadComponent: () => import('./pages/crea-modifica-prenotazione/crea-modifica-prenotazione.component'),
  data: { headerTitle: 'Modifica prenotazione', view: 'EDIT' }
},
{
  path: 'nuova-prenotazione/conferma',
  loadComponent: () => import('./pages/conferma-prenotazione/conferma-prenotazione.component'),
  data: { headerTitle: 'Nuova prenotazione' }
}
```

Se nel progetto esistono resolver di layout (header, breadcrumb, permessi), si aggiungono
qui con `resolve: { … }`.

Il flusso completo è quindi a **tre pagine**: `crea-modifica-*` → submit → `conferma-*`
(pagina di esito che riceve il riepilogo via `router.navigate(..., { state })`).

---

## 8. Il flusso dei dati, in sintesi

```
rotta (data.view + :id)
        │
        ▼
CreaModificaComponent ── constructor ──► FormService.createMainForm()
        │                                 FormService.createCompilazioneRapidaForm()
        │
        ├── ngOnInit ──► liste (opzioni select) ──► signal
        │           └──► [EDIT] ApiService.dettaglio() ──► FormService.populateFromDettaglio()
        │
        ├── form.controls.<lista>.valueChanges
        │        └──► RigheService.syncFromSelectedItems()
        │                 ├─► crea/riusa FormGroup di riga (FormService.createRowFormGroup)
        │                 ├─► crea/riusa rowState (ValidationService)
        │                 └─► ValidationService.setupRowValidation() sulle righe nuove
        │
        ├── form.valueChanges ──► ValidationService.updateSalvaDisabled() ──► signal isSalvaDisabled
        │
        └── onSalva ──► QueryService.getSalva/getEdit
                          └─► RequestModel.generateModel(form, formArray, isEdit)
                                └─► ApiService.post/patch
                                      └─► router.navigate(conferma, { state: riepilogo })
```

---

## 9. Checklist per replicare il pattern

**Prerequisiti nel progetto di destinazione**

- [ ] `provideRouter(routes, withComponentInputBinding())` — serve per il binding
      `:idEntita` → `input()`
- [ ] `class-transformer` installato + `experimentalDecorators: true` in `tsconfig`
      (opzionale: se non lo usi, i `@Transform`/`@Type` diventano mapping manuale in
      `generateModel`)
- [ ] `dayjs` o equivalente (con `customParseFormat` se si parsano date `DD/MM/YYYY` in
      strict mode)
- [ ] path alias `@/*` → `src/app/*`
- [ ] componenti di form (date-picker, time-picker, select-combo, searchbar) che implementino
      `ControlValueAccessor` ed espongano `hasFormError` / `errorDescription`. **Se il
      progetto di destinazione non ha un design system con queste caratteristiche, questo è
      l'unico punto da riscrivere**: sostituisci i controlli con i tuoi (Angular Material,
      PrimeNG, custom) e togli `schemas: [CUSTOM_ELEMENTS_SCHEMA]` se non usi web component.
- [ ] `TranslatePipe` è opzionale: se non c'è i18n, usa stringhe dirette

**Passi, nell'ordine**

1. Crea la cartella pagina con le 5 sottocartelle: `interfaces/`, `models/`, `services/`,
   `components/`, `configs/`.
2. Scrivi le **interfacce** dei form (principale, riga, compilazione rapida) e dello stato riga.
3. Scrivi il **FormService** — solo `create*` / `populate*` / `patch*`, zero stato.
4. Scrivi il **ValidationService** — signal `rowStates`, `isSalvaDisabled`, costante
   `ERROR_MESSAGES`, aggiornamenti immutabili, `takeUntilDestroyed(this.destroyRef)`.
5. Scrivi il **service delle righe** — signal + `computed` per filtro e paginazione,
   `syncFromSelectedItems` con la mappa di code per riuso e duplicati.
6. Scrivi il **RequestModel** con `static generateModel(form, formArray, isEdit)` e
   `getRawValue()`.
7. Scrivi il **componente pagina**: `export default`, `OnPush`, i tre servizi nei
   `providers`, cablaggio nel constructor, caricamento in `ngOnInit`.
8. Spezza il template in **componenti presentazionali** con `input.required<FormGroup<…>>()`
   e `output()`.
9. Registra le **due rotte** (NEW / EDIT) sullo stesso `loadComponent` con `data.view`
   diverso, più la rotta di conferma.

**Errori tipici da evitare**

- mutare `rowStates()` in place invece di ricrearlo (con OnPush la UI non si aggiorna);
- usare `.value` invece di `getRawValue()` nel model (perdi i campi disabilitati);
- mettere `providedIn: 'root'` sui tre servizi di pagina (lo stato sopravvive alla
  navigazione e la seconda apertura del form parte sporca);
- dimenticare `{ emitEvent: false }` in `removeAtIndex`, che rientra nel sync e cicla;
- usare `takeUntilDestroyed()` senza `destroyRef` fuori dal constructor (errore a runtime).

---

## 10. Quando il pattern è sovradimensionato

Serve tutto questo solo se hai **tutte** queste condizioni: form principale + FormArray di
righe derivate da una selezione, validazioni cross-field non esprimibili con `Validators`,
e riuso del componente tra NEW ed EDIT.

Se ti manca la parte del FormArray, tieni solo il **FormService** e il **RequestModel** e
salta gli altri due servizi: la validazione può stare nei `Validators` del form e lo stato
errore direttamente nei `control.errors`.
