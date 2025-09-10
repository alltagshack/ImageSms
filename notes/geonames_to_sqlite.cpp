#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <sqlite3.h>

// get cities:
//   wget http://download.geonames.org/export/dump/cities15000.zip
//   unzip cities15000.zip
// compile: g++ geonames_to_sqlite.cpp -lsqlite3 -Wall -o geonames_to_sqlite
// usage:   ./geonames_to_sqlite tmp/cities15000.txt ../app/src/main/assets/cities.db

int main(int argc, char *argv[]) {
    std::ifstream infile(argv[1]);
    if (!infile.is_open()) {
        std::cerr << "Error on file open!\n";
        return 1;
    }

    sqlite3* db;
    char* errMsg = nullptr;

    int rc = sqlite3_open(argv[2], &db);
    if (rc) {
        std::cerr << "Error during open process: " << sqlite3_errmsg(db) << std::endl;
        return 1;
    }

    // Tabelle erstellen
    const char* createTableSQL = R"(
        CREATE TABLE IF NOT EXISTS cities (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            latitude REAL,
            longitude REAL
        );
    )";

    rc = sqlite3_exec(db, createTableSQL, 0, 0, &errMsg);
    if (rc != SQLITE_OK) {
        std::cerr << "SQL error: " << errMsg << std::endl;
        sqlite3_free(errMsg);
        sqlite3_close(db);
        return 1;
    }

    // Prepare SQL INSERT Statement
    const char* insertSQL = "INSERT INTO cities (id, name, latitude, longitude) VALUES (?, ?, ?, ?);";
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

        sqlite3_bind_int(stmt, 1, geonameId);
        sqlite3_bind_text(stmt, 2, name.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_double(stmt, 3, lat);
        sqlite3_bind_double(stmt, 4, lon);

        sqlite3_step(stmt);
        sqlite3_reset(stmt);

        count++;
        if (count % 1000 == 0) {
            std::cout << count << " entries addded...\n";
        }
    }

    sqlite3_finalize(stmt);
    sqlite3_close(db);
    infile.close();

    std::cout << "Done! " << count << " added.\n";
    return 0;
}