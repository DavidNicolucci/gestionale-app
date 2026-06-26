package com.gestionale.dominio.imports;

public class ImportMessage {
    public String filePath;     // dove abbiamo salvato il file su disco
    public String fileName;     // nome originale, utile per i log

    public ImportMessage() { }  // costruttore vuoto: serve a Jackson per deserializzare

    public ImportMessage(String filePath, String fileName) {
        this.filePath = filePath;
        this.fileName = fileName;
    }
}