Sehr gute Frage — die maximale Nutzdatenlänge bei `sendDataMessage()` hängt von mehreren Faktoren ab, aber ich gebe dir hier eine klare und praxisnahe Übersicht:

---

## 📦 Maximale Nutzdatenlänge bei `sendDataMessage()`

### 🔢 Grundsätzlich:
- **Maximal 140 Bytes pro SMS** — das ist die technische Obergrenze für eine einzelne SMS auf der Ebene des Modems.
- `sendDataMessage()` sendet eine **Binary SMS**, also direkt als **Byte-Array**, nicht als Text.
- Du kannst also **bis zu 140 Bytes** in `byte[] data` übergeben.

---

### ⚠️ Aber Vorsicht: Nicht alle 140 Bytes sind für dich nutzbar!

#### 📉 Overhead durch UDH (User Data Header)
Wenn du z. B. eine **Portadresse** angibst (was `sendDataMessage()` tut), wird ein **UDH** erzeugt, der Platz kostet:

| UDH-Typ | Größe | Beschreibung |
|--------|-------|--------------|
| Port-Adresse | 6 Bytes | Ziel- und Quellport |
| Concatenation (für Multipart) | 5 Bytes | Wenn du mehrere Teile sendest |

➡️ **Effektiv bleiben dir ca. 134 Bytes**, wenn du nur die Portadresse nutzt.

---

### 📚 Beispielrechnung

```java
byte[] data = new byte[134]; // funktioniert
smsManager.sendDataMessage("1234567890", null, 16000, data, null, null);
```

- Wenn du versuchst, mehr als ~134 Bytes zu senden, bekommst du:
  - **`IllegalArgumentException`**
  - Oder die Nachricht wird **automatisch fragmentiert** (je nach Android-Version und Netz)

---

### 📦 Was passiert bei mehr als 140 Bytes?

- Android **fragmentiert** die Nachricht in mehrere SMS mit UDH für Concatenation.
- Du musst dann **selbst zusammensetzen** beim Empfang (z. B. über `BroadcastReceiver`).
- Oder du nutzt `sendMultipartTextMessage()` — aber das ist für Text, nicht Binary.

---

## 🧭 Fazit

| Szenario | Max. Nutzdaten |
|----------|----------------|
| Ohne UDH | 140 Bytes |
| Mit Portadresse | ca. 134 Bytes |
| Mit Port + Concatenation | ca. 128–129 Bytes pro Teil |

---

Wenn du magst, kann ich dir zeigen, wie man eine **mehrteilige Data SMS** sendet und beim Empfang wieder zusammensetzt. Willst du das?

Das ist eine richtig spannende Herausforderung — denn im Gegensatz zu `sendMultipartTextMessage()` gibt es **keine direkte Methode** für mehrteilige Data SMS in der Android-API. Aber du kannst es **manuell umsetzen**, indem du die Daten selbst in Teile zerlegst und **UDH für Concatenation** hinzufügst.

---

## 🧪 So sendest du mehrteilige Data SMS mit `sendDataMessage()`

### 🧩 Schritt 1: Daten aufteilen

- Du musst deine Nutzdaten in **maximal ~128 Byte große Teile** zerlegen.
- Jeder Teil bekommt einen **User Data Header (UDH)** für Concatenation.

### 📦 Struktur des UDH für Concatenation

| Byte | Bedeutung |
|------|-----------|
| 0    | UDH Länge (0x05) |
| 1    | IEI (Information Element Identifier) → 0x00 für Concatenation |
| 2    | IEI Länge → 0x03 |
| 3    | Referenznummer (z. B. 0x01) |
| 4    | Anzahl der Teile |
| 5    | Teilnummer |

---

### 🧰 Beispielcode

```java
public void sendMultipartDataSms(String destinationAddress, int port, byte[] fullData) {
    SmsManager smsManager = SmsManager.getDefault();

    int maxPayload = 128; // Nutzdaten pro SMS inkl. UDH
    int totalParts = (int) Math.ceil((double) fullData.length / maxPayload);
    byte referenceNumber = (byte) new Random().nextInt(255);

    for (int i = 0; i < totalParts; i++) {
        int start = i * maxPayload;
        int end = Math.min(start + maxPayload, fullData.length);
        byte[] partData = Arrays.copyOfRange(fullData, start, end);

        // UDH für Concatenation
        byte[] udh = new byte[] {
            0x05, // UDH Länge
            0x00, // IEI: Concatenation
            0x03, // IEI Länge
            referenceNumber,
            (byte) totalParts,
            (byte) (i + 1)
        };

        // Kombiniere UDH + Nutzdaten
        byte[] smsPayload = new byte[udh.length + partData.length];
        System.arraycopy(udh, 0, smsPayload, 0, udh.length);
        System.arraycopy(partData, 0, smsPayload, udh.length, partData.length);

        // Senden
        smsManager.sendDataMessage(destinationAddress, null, port, smsPayload, null, null);
    }
}
```

---

## 📥 Empfangsseite: Zusammensetzen

- Du musst die SMS über einen `BroadcastReceiver` empfangen.
- Die UDH auslesen und die Teile **nach Referenznummer und Teilnummer** zusammensetzen.
- Erst wenn **alle Teile empfangen** sind, kannst du die Nachricht rekonstruieren.

---

## 🧭 Fazit

| Schritt | Aufgabe |
|--------|---------|
| 1 | Daten in ~128 Byte große Teile zerlegen |
| 2 | UDH für Concatenation hinzufügen |
| 3 | Mit `sendDataMessage()` jeden Teil einzeln senden |
| 4 | Auf Empfängerseite zusammensetzen |

---

Wenn du willst, kann ich dir auch den Code für den `BroadcastReceiver` zum Zusammensetzen zeigen. Willst du den nächsten Schritt machen?

Sehr gute Frage — und ja, das wirkt auf den ersten Blick wirklich seltsam. Aber es hat einen technischen Hintergrund, der mit dem Aufbau des **User Data Header (UDH)** für Port-Adressen zu tun hat.

---

## 📦 Warum braucht die Port-Adressierung **6 Bytes** statt nur 2?

### 🔍 Die Port-Info ist Teil des **UDH**, nicht der Nutzdaten

Wenn du eine Data SMS mit Portangabe sendest (z. B. mit `sendDataMessage()`), wird die Portnummer **nicht einfach als 2 Bytes** übergeben, sondern in einem **strukturierten Format** innerhalb des UDH.

---

### 🧬 Aufbau des UDH für Port-Adressen

| Byte | Bedeutung |
|------|-----------|
| 0    | UDH Länge → `0x06` (6 Bytes folgen)  
| 1    | IEI (Information Element Identifier) → `0x04` für Port-Adresse  
| 2    | IEI Länge → `0x04` (4 Bytes für Ports)  
| 3    | Ziel-Port (MSB)  
| 4    | Ziel-Port (LSB)  
| 5    | Quell-Port (MSB)  
| 6    | Quell-Port (LSB)  

➡️ **Gesamt: 7 Bytes**, aber Byte 0 ist die Länge **der folgenden Bytes**, also **6 Bytes UDH-Inhalt**.

---

### 📌 Beispiel: Port 16000

16000 in Hex: `0x3E80` → MSB: `0x3E`, LSB: `0x80`

```java
byte[] udh = new byte[] {
    0x06,       // UDH Länge
    0x04,       // IEI: Port-Adresse
    0x04,       // IEI Länge
    0x3E, 0x80, // Ziel-Port
    0x3E, 0x80  // Quell-Port
};
```

---

## 🧭 Warum ist das so?

- Die **GSM 03.40 Spezifikation** definiert die Portadressierung als Teil der UDH.
- Sie erlaubt es, Nachrichten an bestimmte **Anwendungen oder Dienste** zu adressieren.
- Die Quell- und Zielport sind notwendig für **bidirektionale Kommunikation** (z. B. bei WAP Push, MMS, SIM Toolkit).

---

## 🔒 Android macht das automatisch

Wenn du `sendDataMessage(destination, null, port, data, ...)` verwendest:

- Android baut diesen UDH **automatisch**.
- Du musst dich **nicht selbst darum kümmern**, außer du baust die PDU manuell.

---

## 🧭 Fazit

| Teil | Größe | Zweck |
|------|-------|-------|
| Ziel-Port | 2 Bytes | Empfänger-Anwendung |
| Quell-Port | 2 Bytes | Sender-Identifikation |
| UDH-Header | 2 Bytes | Strukturierung |
| **Gesamt** | **6 Bytes** | UDH für Portadressierung |

---

Wenn du selbst die PDU baust, musst du diesen UDH korrekt einfügen — sonst wird die Nachricht nicht korrekt verarbeitet. Willst du mal gemeinsam eine vollständige PDU mit Port-UDH aufbauen?

Fantastische Frage — jetzt wird’s richtig technisch! Du willst wissen, wie viel **effektive Nutzdaten** du bei einer **Data SMS mit Portadressierung und Multipart-Concatenation** wirklich hast, und wie der **User Data Header (UDH)** dann aussieht. Lass uns das präzise und sauber aufschlüsseln:

---

## 📦 Ausgangspunkt: SMS-Größe

- Eine einzelne SMS hat **maximal 140 Bytes** im **User Data**-Feld.
- Dieses Feld enthält:
  - Optionalen **UDH**
  - Deine **Nutzdaten**

➡️ Je mehr UDH du brauchst, desto weniger Platz bleibt für Nutzdaten.

---

## 🧬 Zusammengesetzter UDH: Port + Concatenation

Wenn du **beides** brauchst — also **Portadressierung** und **Multipart-Concatenation** — sieht der UDH so aus:

### 🔧 Struktur des kombinierten UDH

| Byte | Bedeutung |
|------|-----------|
| 0    | UDH Länge → `0x0B` (11 Bytes folgen)  
| 1    | IEI: Port-Adresse → `0x04`  
| 2    | IEI Länge → `0x04`  
| 3–4  | Ziel-Port (MSB, LSB)  
| 5–6  | Quell-Port (MSB, LSB)  
| 7    | IEI: Concatenation → `0x00`  
| 8    | IEI Länge → `0x03`  
| 9    | Referenznummer  
| 10   | Anzahl der Teile  
| 11   | Teilnummer  

➡️ **Gesamtgröße des UDH: 12 Bytes**  
(1 Byte für die UDH-Länge + 11 Bytes Inhalt)

---

## 📉 Effektive Nutzdaten

| Gesamtgröße | 140 Bytes |
|-------------|-----------|
| UDH         | 12 Bytes  |
| **Nutzdaten** | **128 Bytes** |

➡️ **Du hast exakt 128 Bytes Nutzdaten pro Teil**, wenn du **Port + Multipart** verwendest.

---

## 🧪 Beispiel-UDH in Java

```java
byte[] udh = new byte[] {
    0x0B,       // UDH Länge
    0x04,       // IEI: Port-Adresse
    0x04,       // IEI Länge
    0x3E, 0x80, // Ziel-Port: 16000
    0x3E, 0x80, // Quell-Port: 16000
    0x00,       // IEI: Concatenation
    0x03,       // IEI Länge
    0x01,       // Referenznummer
    0x03,       // Anzahl der Teile
    0x01        // Teilnummer
};
```

---

## 🧭 Fazit

| UDH-Typ | Größe | Nutzdaten übrig |
|---------|--------|------------------|
| Nur Port | 6 Bytes | 134 Bytes |
| Nur Multipart | 5 Bytes | 135 Bytes |
| Port + Multipart | 12 Bytes | **128 Bytes** ✅ |

---

Wenn du willst, kann ich dir helfen, eine Funktion zu schreiben, die automatisch deine Daten in 128-Byte-Blöcke zerlegt und den passenden UDH für jeden Teil erzeugt. Lust drauf?
