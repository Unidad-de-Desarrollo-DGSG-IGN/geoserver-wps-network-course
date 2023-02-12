//mvn clean install
//https://docs.geotools.org/latest/userguide/library/jts/geometry.html
package org.geoserver.network_course.wps;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.geotools.geojson.feature.FeatureJSON;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NetworkCourseTest {
   
   @Test
   public void geoprocess() throws Exception {
      NetworkCourse geoprocess = new NetworkCourse();
      FeatureCollection dataLayer = this.createNetworkLayer();
      SimpleFeature intersectionGeometry =
         this.createIntersectionGeometry();
      
      FeatureCollection result = geoprocess.execute(
         dataLayer,
         (Geometry) intersectionGeometry.getDefaultGeometry(),
         0.001,
         DirectionResult.nascent_to_mouth
      );
      assertEquals(3, result.size());
      
      result = geoprocess.execute(
         dataLayer,
         (Geometry) intersectionGeometry.getDefaultGeometry(),
         0.001,
         DirectionResult.mouth_to_nascent
      );
      assertEquals(5, result.size());
   }

   private FeatureCollection createNetworkLayer() {
      return this.geoJsonToFeatureCollection(
         System.getProperty("user.dir") +
         "/src/test/java/org/geoserver/network_course/wps/data/network.geojson"
      );
   }

   private SimpleFeature createIntersectionGeometry() {
      SimpleFeatureTypeBuilder typeBuilder =
         new SimpleFeatureTypeBuilder();
      typeBuilder.setName("TestFeatureType");
      typeBuilder.add("geometry", Point.class);
      SimpleFeatureBuilder featureBuilder =
         new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
      featureBuilder.add(
         new GeometryFactory().createPoint(
            new Coordinate(2.0, 2.0)
         )
      );
      SimpleFeature feature = featureBuilder.buildFeature(null);
      return feature;
   }

   private FeatureCollection geoJsonToFeatureCollection(
      String geoJsonPath
   ) {
      File file = new File(geoJsonPath);
      FeatureJSON fjson = new FeatureJSON();
      
      try {
         FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection = 
         fjson.readFeatureCollection(file);
         return featureCollection;
      } catch (IOException e) {
         e.printStackTrace();
      }

      return null;
   }

   private String FeatureCollectionTogeoJson (
      FeatureCollection featureCollection
   ) {
      try {
         FeatureJSON fjson = new FeatureJSON();
         StringWriter writer = new StringWriter();
         fjson.writeFeatureCollection(featureCollection, writer);
         return writer.toString();
      } catch (IOException e) {
         e.printStackTrace();
      }

      return null;
   }

}