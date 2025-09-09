Das ist ein spannendes Projekt – du möchtest also auf einem Android-Gerät eine lokale (offline) Möglichkeit haben, aus einer gegebenen **Latitude/Longitude-Koordinate** ungefähr (±20 km Genauigkeit) zu ermitteln, **zu welchem Ort** diese Koordinate gehört. Ideal für Anwendungen mit Offline-Karten, Geo-Tagging oder Standort-Zuweisungen ohne Internet.

### Was du brauchst:

1. Eine lokale Datenbank mit Orten und deren Koordinaten.
2. Room als ORM (Object Relational Mapping) zum Zugriff auf SQLite unter Android.
3. Einen Algorithmus, um für eine gegebene Koordinate den nächstgelegenen Ort in deiner Datenbank zu finden.

---

## 🔧 Projektstruktur & Komponenten

### 1. **Room Datenbank (OrtEntity)**

```kotlin
@Entity(tableName = "orte")
data class OrtEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
```

### 2. **DAO Interface**

```kotlin
@Dao
interface OrtDao {
    @Query("SELECT * FROM orte")
    suspend fun getAlleOrte(): List<OrtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrt(ort: OrtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrte(orte: List<OrtEntity>)
}
```

### 3. **Room-Datenbank**

```kotlin
@Database(entities = [OrtEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ortDao(): OrtDao
}
```

### 4. **Repository mit Entfernungsberechnung**

```kotlin
class OrtsRepository(private val ortDao: OrtDao) {

    fun findeNaechstenOrt(lat: Double, lon: Double, maxDistKm: Double = 20.0): OrtEntity? {
        val orte = runBlocking { ortDao.getAlleOrte() }
        return orte.minByOrNull {
            entfernungInKm(lat, lon, it.latitude, it.longitude)
        }?.takeIf {
            entfernungInKm(lat, lon, it.latitude, it.longitude) <= maxDistKm
        }
    }

    private fun entfernungInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Erd-Radius in Kilometern
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
```

---

## 🗺️ Daten für deine Datenbank (z. B. Orte mit Koordinaten)

Du kannst dir eine kleine Liste z. B. von größeren Städten oder Dörfern erstellen. Hier ein Beispiel:

```kotlin
val initialOrte = listOf(
    OrtEntity(name = "Berlin", latitude = 52.5200, longitude = 13.4050),
    OrtEntity(name = "Hamburg", latitude = 53.5511, longitude = 9.9937),
    OrtEntity(name = "München", latitude = 48.1351, longitude = 11.5820),
    OrtEntity(name = "Leipzig", latitude = 51.3397, longitude = 12.3731)
)
```

Diese könntest du beim ersten Start der App in die Datenbank einfügen (z. B. in einem `Prepopulate`-Mechanismus oder `ViewModel` beim Start).

---

## 🧪 Beispielnutzung

```kotlin
val repository = OrtsRepository(appDatabase.ortDao())

val aktuelleLatitude = 52.4
val aktuelleLongitude = 13.1

val ort = repository.findeNaechstenOrt(aktuelleLatitude, aktuelleLongitude)
if (ort != null) {
    println("Nächster Ort: ${ort.name}")
} else {
    println("Kein Ort in 20 km Umkreis gefunden.")
}
```

---

## 📝 Hinweise

* Die Berechnung nutzt die Haversine-Formel zur Ermittlung der Distanz zwischen zwei GPS-Koordinaten.
* Wenn du mehr Orte brauchst, könntest du z. B. OpenStreetMap oder Geonames nutzen, um eine größere Menge an Städten mit Koordinaten vorzuladen.
* Wenn du Performance brauchst, könntest du z. B. Bounding-Boxen oder Kachelung zur Vorauswahl nutzen, bevor du die Distanz berechnest.

---

## ✅ Zusammenfassung

Mit Room kannst du problemlos eine kleine Offline-Datenbank mit Orten aufbauen. Mithilfe einfacher Distanzberechnungen kannst du bei gegebener Koordinate den nächsten Ort bestimmen. Für ±20 km Genauigkeit reicht eine grobe Liste größerer Städte oder Orte vollkommen aus – das hält die Datenbank klein und performant.

---

Möchtest du ein Beispielprojekt als Template (z. B. GitHub-Link oder Android Studio-Projekt)? Oder soll ich dir helfen, die Orte aus einer Quelle wie GeoNames oder OpenStreetMap zu extrahieren und im CSV-Format zu liefern?


Perfekt – wenn du unter **Debian Linux** arbeitest und die **GeoNames `cities500.txt`** verwenden willst, dann ist das ein sehr effizienter Weg. Wir bauen die Datenbank **offline** aus der Textdatei auf und nutzen **C++**, um ein Tool zu schreiben, das die Datei liest und eine **SQLite-Datenbank** generiert, die dann von deiner Android-App (Room) verwendet werden kann.

---

## 🧭 Überblick

### Was du bekommst:

✅ C++-Tool zum Parsen der `cities500.txt`
✅ Erzeugung einer `orte.db` SQLite-Datenbank
✅ Kompatibel mit Android Room (für spätere Nutzung)
✅ Tipps zur Integration in Android

---

## 🔹 1. GeoNames-Daten: `cities500.txt`

### Herunterladen:

```bash
wget http://download.geonames.org/export/dump/cities500.zip
unzip cities500.zip
```

### Format der Datei (`cities500.txt`):

Tab-getrennte Datei, jede Zeile ist ein Ort. Wichtigste Spalten:

| Spalte | Inhalt                   | Beispiel   |
| ------ | ------------------------ | ---------- |
| 1      | GeoNameID                | `2800866`  |
| 2      | Name                     | `Berlin`   |
| 5      | Breitengrad (Latitude)   | `52.52437` |
| 6      | Längengrad (Longitude)   | `13.41053` |
| 15     | Bevölkerung (Population) | `3426354`  |

---

## 🔧 2. SQLite-Datenbank aus GeoNames per C++

### Abhängigkeit installieren:

```bash
sudo apt install libsqlite3-dev
```

### 💡 Ziel:

Wir generieren eine Tabelle:

```sql
CREATE TABLE orte (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    latitude REAL,
    longitude REAL,
    population INTEGER
);
```

---

## 🧑‍💻 3. C++ Code: `geonames_to_sqlite.cpp`

```cpp
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <sqlite3.h>

int main() {
    std::ifstream infile("cities500.txt");
    if (!infile.is_open()) {
        std::cerr << "Fehler beim Öffnen der Datei.\n";
        return 1;
    }

    sqlite3* db;
    char* errMsg = nullptr;

    int rc = sqlite3_open("orte.db", &db);
    if (rc) {
        std::cerr << "Kann DB nicht öffnen: " << sqlite3_errmsg(db) << std::endl;
        return 1;
    }

    // Tabelle erstellen
    const char* createTableSQL = R"(
        CREATE TABLE IF NOT EXISTS orte (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            latitude REAL,
            longitude REAL,
            population INTEGER
        );
    )";

    rc = sqlite3_exec(db, createTableSQL, 0, 0, &errMsg);
    if (rc != SQLITE_OK) {
        std::cerr << "SQL Fehler: " << errMsg << std::endl;
        sqlite3_free(errMsg);
        sqlite3_close(db);
        return 1;
    }

    // Prepare SQL INSERT Statement
    const char* insertSQL = "INSERT INTO orte (id, name, latitude, longitude, population) VALUES (?, ?, ?, ?, ?);";
    sqlite3_stmt* stmt;
    sqlite3_prepare_v2(db, insertSQL, -1, &stmt, nullptr);

    std::string line;
    int count = 0;

    while (std::getline(infile, line)) {
        std::istringstream ss(line);
        std::string field;
        std::string fields[20];
        int i = 0;

        while (std::getline(ss, field, '\t') && i < 20) {
            fields[i++] = field;
        }

        int geonameId = std::stoi(fields[0]);
        std::string name = fields[1];
        double lat = std::stod(fields[4]);
        double lon = std::stod(fields[5]);
        int population = std::stoi(fields[14]);

        sqlite3_bind_int(stmt, 1, geonameId);
        sqlite3_bind_text(stmt, 2, name.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_double(stmt, 3, lat);
        sqlite3_bind_double(stmt, 4, lon);
        sqlite3_bind_int(stmt, 5, population);

        sqlite3_step(stmt);
        sqlite3_reset(stmt);

        count++;
        if (count % 10000 == 0) {
            std::cout << count << " Orte verarbeitet...\n";
        }
    }

    sqlite3_finalize(stmt);
    sqlite3_close(db);
    infile.close();

    std::cout << "Fertig! Insgesamt " << count << " Orte eingefügt.\n";
    return 0;
}
```

---

## 🔨 4. Kompilieren & Ausführen

```bash
g++ geonames_to_sqlite.cpp -lsqlite3 -o geonames_to_sqlite
./geonames_to_sqlite
```

Das erzeugt die Datei `orte.db` mit deiner Offline-Ortstabelle.

---

## 📱 5. Integration in Android Room

1. Kopiere `orte.db` ins Assets-Verzeichnis deiner Android-App:

```
app/src/main/assets/orte.db
```

2. Room mit **Prepackaged Database** verwenden:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "orte.db")
    .createFromAsset("orte.db")
    .build()
```

3. `OrtEntity` muss die Struktur der Tabelle 1:1 widerspiegeln.

---

## 💡 Tipps & Extras

* Willst du nur bestimmte Länder (z. B. nur DE), kannst du vor dem Einfügen `fields[8] == "DE"` prüfen.
* Willst du nur größere Orte, prüfe auf `population > 10000`.
* Du kannst die DB auch mit `sqlite3` inspizieren:

```bash
sqlite3 orte.db
sqlite> SELECT name, latitude, longitude FROM orte LIMIT 10;
```

---

## ✅ Zusammenfassung

| Schritt                | Tool / Technik               |
| ---------------------- | ---------------------------- |
| GeoNames herunterladen | `wget`, `unzip`              |
| Textdatei parsen       | C++ + STL                    |
| SQLite erzeugen        | `libsqlite3`                 |
| Datenbank füllen       | mit vorbereiteten INSERTS    |
| In Android nutzen      | `Room` + `createFromAsset()` |

---

Möchtest du optional noch:

* **Nur bestimmte Regionen** einfügen?
* **Koordinaten auf z. B. 4 Nachkommastellen runden**?
* **Eine exportierbare `.csv` oder `.json` Version**?

Sag einfach Bescheid.




