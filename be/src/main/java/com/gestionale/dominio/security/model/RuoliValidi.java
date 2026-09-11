package com.gestionale.dominio.security.model;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * Almeno un ruolo, e ogni elemento della lista dev'essere un {@link Ruolo} esistente.
 *
 * Il vincolo sta sulla lista intera e non sui singoli elementi: cosi' l'errore
 * arriva al client sotto il campo "ruoli", e non sotto un "&lt;list element&gt;"
 * che il frontend non saprebbe a cosa attaccare.
 *
 * Anche la lista vuota la controlla questo vincolo e non un @NotEmpty: il messaggio
 * deve elencare i ruoli ammessi, e in un'annotazione andrebbero scritti a mano,
 * mentre qui arrivano dall'enum.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RuoliValidi.Validatore.class)
public @interface RuoliValidi {

    String message() default "Ruolo non valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validatore implements ConstraintValidator<RuoliValidi, Collection<String>> {

        @Override
        public boolean isValid(Collection<String> ruoli, ConstraintValidatorContext context) {
            if (ruoli == null || ruoli.isEmpty()) {
                return errore(context, "Indicare almeno un ruolo: i ruoli ammessi sono " + Ruolo.ELENCO);
            }
            if (!ruoli.stream().allMatch(r -> Ruolo.da(r).isPresent())) {
                return errore(context, "Ruolo non valido: i ruoli ammessi sono " + Ruolo.ELENCO);
            }
            return true;
        }

        /**
         * Il messaggio contiene solo testo nostro, di proposito NON il valore
         * sbagliato: il template passa dall'interpolazione di Hibernate Validator, e
         * metterci dentro testo arrivato dal client aprirebbe la porta all'iniezione
         * di espressioni.
         */
        private static boolean errore(ConstraintValidatorContext context, String messaggio) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(messaggio).addConstraintViolation();
            return false;
        }
    }
}
