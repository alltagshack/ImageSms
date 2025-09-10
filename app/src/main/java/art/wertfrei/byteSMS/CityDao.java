package art.wertfrei.byteSMS;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
interface CityDao {
    @Query("SELECT * FROM cities")
    List<CityEntity> getCities();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCity(CityEntity c);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCities(List<CityEntity> cc);
}

