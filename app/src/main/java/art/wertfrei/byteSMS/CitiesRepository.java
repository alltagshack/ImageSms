package art.wertfrei.byteSMS;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CitiesRepository {
    private CityDao cityDao;

    public CitiesRepository(CityDao cityDao) {
        this.cityDao = cityDao;
    }

    public List<CityDistance> nextCities(double lat, double lon, int limit) {
        List<CityEntity> cities = cityDao.getCities();
        List<CityDistance> distList = new ArrayList<>();

        for (CityEntity city : cities) {
            double dist = distanceKm(lat, lon, city.getLatitude(), city.getLongitude());
            distList.add(new CityDistance(city, dist));
        }
        Log.d("bytesms", "cities: " + cities.size());

        Collections.sort(distList, new Comparator<CityDistance>() {
            @Override
            public int compare(CityDistance o1, CityDistance o2) {
                return Double.compare(o1.distance, o2.distance);
            }
        });

        List<CityDistance> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, distList.size()); i++) {
            CityDistance cd = distList.get(i);
            result.add(cd);
        }

        return result;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
