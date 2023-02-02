//mvn clean install
//https://docs.geotools.org/latest/userguide/library/jts/geometry.html
package org.geoserver.network_course.wps;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

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

@DescribeProcess(title="NetworkCourse", description="Compute the course into a network from a geometry")
public class NetworkCourse implements GeoServerProcess {
   
   @DescribeResult(name="result", description="Subset of data from the feature collection that specify the course into the network")
   public FeatureCollection execute(
      @DescribeParameter(name="geom", description="Feature collection containing all base geometries of the network")
      FeatureCollection dataLayer,
      @DescribeParameter(name="intersectionGeometry", description="Geometry to intersect")
      Geometry intersectionGeometry,
      @DescribeParameter(name="tolerance", description="Tolerance for intersection calculation (in feature collection unit of measure)")
      Float intersectionTolerance
   ) {
      return 
         this.getAllLinesTouchesOnNetwork(
            dataLayer,
            this.getIntersectedGeomsByAPoint(
               dataLayer,
               intersectionGeometry,
               intersectionTolerance
            )
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

      try {
         Filter filter = CQL.toFilter("INTERSECTS(the_geom, " + bufferToIntersect.toText() + ")");
         FeatureIterator<SimpleFeature> iter = geomNetwork.subCollection(filter).features();
         while (iter.hasNext()) {
               SimpleFeature feature = iter.next();
               intersectingFeatures.add(feature);
         }
      } catch (CQLException e) {
         e.printStackTrace();
      }
      
      return intersectingFeatures;
   }

   private FeatureCollection getAllLinesTouchesOnNetwork(
      FeatureCollection geomNetwork,
      FeatureCollection intersectingFeatures
   ) {
      int linesPreviouslyFound = intersectingFeatures.size();
      DefaultFeatureCollection intersectingFC = new DefaultFeatureCollection();
      intersectingFC.addAll(intersectingFeatures);
      try (FeatureIterator<SimpleFeature> iterator = intersectingFeatures.features()) {
         while (iterator.hasNext()) {
            SimpleFeature actualFeature = iterator.next();
            MultiLineString linesToIntersect = (MultiLineString) actualFeature.getDefaultGeometry();
            try {
               Filter filter = CQL.toFilter("INTERSECTS(the_geom, " + linesToIntersect.toText() + ")");
               FeatureIterator<SimpleFeature> iter = geomNetwork.subCollection(filter).features();
               while (iter.hasNext()) {
                  SimpleFeature feature = iter.next();
                  MultiLineString linesFound = (MultiLineString) feature.getDefaultGeometry();
                  for (int i = 0; i < linesFound.getNumGeometries(); i++) {
                     for (int j = 0; j < linesToIntersect.getNumGeometries(); j++) {
                        LineString lineFound = (LineString) linesFound.getGeometryN(i);
                        LineString lineToIntersect = (LineString) linesToIntersect.getGeometryN(j);
                        // if (lineFound.getStartPoint().equals(lineToIntersect.getEndPoint())) {
                        if (lineFound.getStartPoint().isWithinDistance(lineToIntersect.getEndPoint(), 0.0001)) {
                           intersectingFC.add(feature);
                        }
                     }
                  }                                    
               }
            } catch (CQLException e) {
               e.printStackTrace();
            }
         }
      }

      if (linesPreviouslyFound == intersectingFC.size() || intersectingFC.size() >= geomNetwork.size()) {
         return intersectingFC;
      }

      return this.getAllLinesTouchesOnNetwork(
         geomNetwork,
         intersectingFC
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
}