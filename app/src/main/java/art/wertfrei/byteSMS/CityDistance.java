package art.wertfrei.byteSMS;

public class CityDistance {
    public CityEntity city;
    public double distance;

    CityDistance(CityEntity city, double distance) {
        this.city = city;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return city.getName() + " (" + String.format("%.2f", distance) + " km)";
    }
}
