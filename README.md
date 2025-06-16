# TreniCal - Sistema di Gestione Treni

## Descrizione
TreniCal è un sistema distribuito per la gestione dei biglietti e dei servizi ferroviari. Questo progetto include il microservizio Ruby per la gestione dei dati dei treni, un client JavaFX per l'interazione utente, e una serie di servizi gRPC per la comunicazione tra i componenti.

## Requisiti
- Ruby (per eseguire il microservizio)
- Java (JDK 8 o superiore)
- Maven (per il client JavaFX)

## Esecuzione del Progetto

### Passo 1: Avviare il Server Ruby
1. Apri un terminale e spostati nella directory del microservizio Ruby:
    ```bash
    cd "C:\..\TreniCal\treni-cal-proto\src\main\proto\ruby_viaggiatreno_microservizio"
    ```
2. Avvia il server con il comando:
    ```
    ruby server.rb
    ```

### Passo 2: Avviare il Servizio Java (TreniCal)
1. Apri un nuovo terminale e spostati nella directory di `treni-cal-shading\target`:
    ```bash
    cd "C:\..\TreniCal\treni-cal-shading\target"
    ```
2. Esegui il comando per avviare il servizio:
    ```bash
    java -jar treni-cal-shading-1.0-SNAPSHOT.jar
    ```

### Passo 3: Avviare il Client JavaFX
1. Apri un terzo terminale e spostati nella directory del client JavaFX:
    ```bash
    cd "C:\..\TreniCal\treni-cal-client"
    ```
2. Avvia la GUI JavaFX con Maven:
    ```bash
    mvn javafx:run
    ```

## Struttura del Progetto
- **Ruby Viaggiatreno Microservizio**: Gestisce i dati relativi ai treni e la comunicazione con il server.
- **Java Client (TreniCal)**: Interfaccia grafica per gli utenti, sviluppata con JavaFX.
- **gRPC Server**: Fornisce l'accesso ai servizi tramite protocolli gRPC.



