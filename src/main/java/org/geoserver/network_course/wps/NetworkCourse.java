//mvn clean install
//https://docs.geotools.org/latest/userguide/library/jts/geometry.html
package org.geoserver.network_course.wps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;

import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geoserver.wps.gs.GeoServerProcess;

import org.geotools.data.FeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.geotools.data.DataUtilities;
import org.geotools.feature.FeatureIterator;
import org.locationtech.jts.algorithm.PointLocation;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiLineString;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.data.DataUtilities;

import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;

import org.geotools.data.Parameter;

@DescribeProcess(title="NetworkCourse", description="Compute the course into a network from a geometry")
public class NetworkCourse implements GeoServerProcess {
   
   public enum DirectionResult {
      nascent_to_mouth,
      mouth_to_nascent
    }

   @DescribeResult(name="result", description="Subset of data from the feature collection that specify the course into the network")
   public FeatureCollection execute(
      @DescribeParameter(name="geom", description="Feature collection containing all base geometries of the network")
      FeatureCollection dataLayer,
      @DescribeParameter(name="intersectionGeometry", description="Geometry to intersect")
      Geometry intersectionGeometry,
      @DescribeParameter(name="tolerance", description="Tolerance for intersection calculation (in feature collection unit of measure)")
      Float intersectionTolerance,
      @DescribeParameter(name="directionResult", description="Option to specify the direction of the result", min = 1, max = 1)
      DirectionResult directionResult
   ) {
      return 
         this.getAllLinesTouchesOnNetwork(
            dataLayer,
            this.getIntersectedGeomsByAPoint(
               dataLayer,
               intersectionGeometry,
               intersectionTolerance
            ),
            directionResult
         );
   }

   /** Return the intersected Geometries into a Network by a single Point */
   private FeatureCollection getIntersectedGeomsByAPoint(
      FeatureCollection geomNetwork,
      Geometry pointToIntersect,
      Float bufferSize
   ) {
      DefaultFeatureCollection intersectingFeatures = new DefaultFeatureCollection();
      Geometry bufferToIntersect = pointToIntersect.buffer(bufferSize);

      FeatureIterator<SimpleFeature> intersectedFeaturesIterator =
         this.getIntersectedSubcollection(geomNetwork, bufferToIntersect);
      while (intersectedFeaturesIterator.hasNext()) {
            SimpleFeature feature = intersectedFeaturesIterator.next();
            intersectingFeatures.add(feature);
      }

      return intersectingFeatures;
   }

   private FeatureCollection getAllLinesTouchesOnNetwork(
      FeatureCollection geomNetwork,
      FeatureCollection intersectedFeatures,
      DirectionResult directionResult
   ) {
      int linesPreviouslyFound = intersectedFeatures.size();

      DefaultFeatureCollection intersectingFC = new DefaultFeatureCollection();
      intersectingFC.addAll(intersectedFeatures);

      intersectingFC.addAll(
         this.getNewFeaturesThatIntersect(
            geomNetwork,
            intersectedFeatures,
            directionResult
         )
      );

      if (linesPreviouslyFound == intersectingFC.size()
          || intersectingFC.size() >= geomNetwork.size()
      ) {
         return intersectingFC;
      }

      return this.getAllLinesTouchesOnNetwork(
         geomNetwork,
         intersectingFC,
         directionResult
      );
   }

   private Geometry convert2D(Geometry g3D){
      // copy geometry
      Geometry g2D = (Geometry) g3D.clone();
      // set new 2D coordinates
      for(Coordinate c : g2D.getCoordinates()){
         c.setCoordinate(new Coordinate(c.x, c.y));
      }
      return g2D;
   }

   private FeatureCollection getNewFeaturesThatIntersect(
      FeatureCollection geomNetwork,
      FeatureCollection baseFeaturesToIntersect,
      DirectionResult directionResult
   ) {
      DefaultFeatureCollection intersectingFC = new DefaultFeatureCollection();
      try (FeatureIterator<SimpleFeature> baseFeaturesIterator = baseFeaturesToIntersect.features()) {
         while (baseFeaturesIterator.hasNext()) {
            SimpleFeature actualIntersectingFeature = baseFeaturesIterator.next();
            intersectingFC.addAll(
               this.getNewFeaturesThatIntersectWithABaseFeature(
                  geomNetwork,
                  actualIntersectingFeature,
                  directionResult
               )
            );
         }
      }

      return intersectingFC;
   }

   private FeatureCollection getNewFeaturesThatIntersectWithABaseFeature(
      FeatureCollection geomNetwork,
      SimpleFeature baseFeatureToIntersect,
      DirectionResult directionResult
   ) {
      DefaultFeatureCollection intersectingFC = new DefaultFeatureCollection();
      MultiLineString linesToIntersect = (MultiLineString) baseFeatureToIntersect.getDefaultGeometry();
      FeatureIterator<SimpleFeature> intersectedFeaturesIterator =
         this.getIntersectedSubcollection(
            geomNetwork,
            linesToIntersect
         );
      while (intersectedFeaturesIterator.hasNext()) {
         SimpleFeature intersectedFeature = intersectedFeaturesIterator.next();
         MultiLineString linesFound = (MultiLineString) intersectedFeature.getDefaultGeometry();
         for (int i = 0; i < linesFound.getNumGeometries(); i++) {
            for (int j = 0; j < linesToIntersect.getNumGeometries(); j++) {
               if (this.existsPointIntersection(
                  (LineString) linesFound.getGeometryN(i),
                  (LineString) linesToIntersect.getGeometryN(j),
                  directionResult
               )) {
                  intersectingFC.add(intersectedFeature);
               }
            }
         }                                    
      }

      return intersectingFC;
   }

   private FeatureIterator<SimpleFeature> getIntersectedSubcollection(
      FeatureCollection geomNetwork,
      Geometry geometryToIntersect
   ) {
      try {
         Filter filter = CQL.toFilter("INTERSECTS(the_geom, " + geometryToIntersect.toText() + ")");
         FeatureIterator<SimpleFeature> intersectedFeaturesIterator = geomNetwork.subCollection(filter).features();
         return intersectedFeaturesIterator;
      } catch (CQLException e) {
         e.printStackTrace();
      }

      return null;
   }

   private boolean existsPointIntersection(
      LineString lineFound,
      LineString lineToIntersect,
      DirectionResult directionResult
   ) {

      List<Point> points = this.getPointsToIntersect(
         lineFound,
         lineToIntersect,
         directionResult
      );

      return points.get(0)
         .isWithinDistance(
            points.get(1), 
            0.0001
         );
   }

   private List<Point> getPointsToIntersect(
      LineString lineFound,
      LineString lineToIntersect,
      DirectionResult directionResult
   ) {
      Point pointFound = lineFound.getStartPoint();
      Point pointToIntersect = lineToIntersect.getEndPoint();
      if (directionResult == DirectionResult.mouth_to_nascent) {
         pointFound = lineFound.getEndPoint();
         pointToIntersect = lineToIntersect.getStartPoint();
      }

      List<Point> points = new ArrayList<Point>();
      points.add(pointFound);
      points.add(pointToIntersect);
      return points;
   }

}