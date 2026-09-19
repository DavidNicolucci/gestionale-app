import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('si crea', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  /**
   * `App` è solo il guscio: non disegna niente di suo, monta l'outlet e lascia
   * fare al router. Se l'outlet sparisce dal template l'applicazione non mostra
   * più nessuna pagina, e a parte questo controllo non se ne accorgerebbe
   * nessuno finché non la si apre.
   */
  it('monta il router-outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const contenuto = fixture.nativeElement as HTMLElement;
    expect(contenuto.querySelector('router-outlet')).not.toBeNull();
  });
});
