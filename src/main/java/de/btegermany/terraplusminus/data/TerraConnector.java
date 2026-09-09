package de.btegermany.terraplusminus.data;

import de.btegermany.terraplusminus.Terraplusminus;
import net.buildtheearth.terraminusminus.TerraminusminusService;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;

import java.util.concurrent.CompletableFuture;


/**
 * @author Noah Husby
 */

public class TerraConnector {

    private final TerraminusminusService terraminusminusService;

    public TerraConnector(TerraminusminusService terraminusminusService) {
        this.terraminusminusService = terraminusminusService;
    }

    /**
     * Gets the geographical location from in-game coordinates
     *
     * @param x X-Axis in-game
     * @param z Z-Axis in-game
     * @return The geographical location (Long, Lat)
     */
    public double[] toGeo(double x, double z) {
        try {
            return terraminusminusService.toGeo(x, z);
        } catch (OutOfProjectionBoundsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets in-game coordinates from geographical location
     *
     * @param lon Geographical Longitude
     * @param lat Geographic Latitude
     * @return The in-game coordinates (x, z)
     */
    public double[] fromGeo(double lon, double lat) {
        try {
            return terraminusminusService.fromGeo(lon, lat);
        } catch (OutOfProjectionBoundsException e) {
            throw new RuntimeException(e);
        }
    }


    public CompletableFuture<Double> getHeight(double x, double z) {
        return terraminusminusService.getHeight(x, z);
    }


}