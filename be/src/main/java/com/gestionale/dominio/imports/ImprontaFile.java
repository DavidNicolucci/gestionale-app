package com.gestionale.dominio.imports;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * L'impronta di un file: SHA-256 del contenuto, in esadecimale.
 *
 * Serve a rispondere a "questo file l'abbiamo gia' importato?". Sul NOME non si puo'
 * fare: due esportazioni dello stesso periodo si chiamano tutte e due "ore.xlsx", e lo
 * stesso file rinominato resta lo stesso file. Sul contenuto invece la risposta e'
 * esatta: basta un byte diverso e l'impronta cambia.
 */
final class ImprontaFile {

    private ImprontaFile() { }

    /** Legge il file a blocchi: un Excel da 5.000 righe non va caricato in memoria per farne l'hash. */
    static String sha256(Path file) throws IOException {
        MessageDigest digest = creaDigest();
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            int letti;
            while ((letti = in.read(buffer)) > 0) {
                digest.update(buffer, 0, letti);
            }
        }
        return esadecimale(digest.digest());
    }

    private static MessageDigest creaDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e' obbligatorio in ogni JVM: se manca non c'e' niente da gestire.
            throw new IllegalStateException("SHA-256 non disponibile su questa JVM", e);
        }
    }

    private static String esadecimale(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
